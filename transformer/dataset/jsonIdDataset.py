import os
from itertools import chain

from sklearn.model_selection import train_test_split
from torchtext import data
from tqdm.contrib import tmap

from dataset.baseIdDataset import BaseIdDataset
from utils import JAVA_SMALL_TEST, JAVA_SMALL_VAL, JAVA_SMALL_TRAIN
from utils import read_json


class JSONIdDataset(BaseIdDataset):
    def setup(self):
        dataset = {}
        train_files, val_files, test_files = [], [], []
        if os.path.isfile(self.dataset_path):
            # Reading dataset
            dataset = read_json(self.dataset_path)

            # Splitting dataset by files
            train_files, test_files = train_test_split(list(dataset.keys()), train_size=0.8)
            test_files, val_files = train_test_split(test_files, train_size=0.5)
        else:
            for file in os.listdir(self.dataset_path):
                file_path = os.path.join(self.dataset_path, file)
                if os.path.isfile(file_path) & file.endswith("_dataset.json"):
                    project_name = file.replace("_dataset.json", "")
                    print(f"Reading dataset {project_name}...")
                    _dataset = read_json(file_path)
                    dataset.update(_dataset)
                    if self.dataset_split_strategy == "mixed":
                        if len(_dataset) >= 10:
                            _train_files, _test_files = train_test_split(list(_dataset.keys()), train_size=0.8)
                            _test_files, _val_files = train_test_split(_test_files, train_size=0.5)
                            train_files.extend(_train_files)
                            val_files.extend(_val_files)
                            test_files.extend(_test_files)
                        else:
                            test_files.extend(list(_dataset.keys()))
                    elif self.dataset_split_strategy == "java-small":
                        if project_name in JAVA_SMALL_TRAIN:
                            print("Sending to train...")
                            train_files.extend(_dataset.keys())
                        elif project_name in JAVA_SMALL_VAL:
                            print("Sending to val...")
                            val_files.extend(_dataset.keys())
                        elif project_name in JAVA_SMALL_TEST:
                            print("Sending to test...")
                            test_files.extend(_dataset.keys())
                        else:
                            print(f"Project {project_name} is not in java-small dataset.")
                    else:
                        raise NotImplementedError()

        print("Building train dataset...")
        self.train = self.make_dataset(train_files, dataset)
        print("Building val dataset...")
        self.val = self.make_dataset(val_files, dataset)
        print("Building test dataset...")
        self.test = self.make_dataset(test_files, dataset)

    def make_dataset(self, files, dset):
        example_fields = {'variable': ('target', self.target_field),
                          'ngrams': ('usages', self.usage_field)}
        dataset_fields = {'target': self.target_field,
                          'usages': self.usage_field}
        dataset = data.Dataset(
            list(tmap(
                lambda d: data.Example.fromdict(d, example_fields),
                chain(*map(lambda f: dset[f], files)),
                mininterval=0.5
            )),
            dataset_fields)
        for file in files:
            del dset[file]
        return dataset

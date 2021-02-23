import os
from itertools import chain

import pytorch_lightning as pl
import torch
from sklearn.model_selection import train_test_split
from torchtext import data
from tqdm.contrib import tmap

from utils import read_json, camelCaseTokenizer, reverseSplit


class IdDataModule(pl.LightningDataModule):
    JAVA_SMALL_TRAIN = {"cassandra-dtest-shaded", "gradle", "intellij-community", "presto-root", "wildfly-parent",
                        "elasticsearch", "hibernate-orm", "liferay-portal", "spring"}
    JAVA_SMALL_VAL = {"gdx-parent"}
    JAVA_SMALL_TEST = {"hadoop"}

    def __init__(self, dataset_path, split_strategy, model_cfg):
        super().__init__()
        self.split_strategy = split_strategy
        self.dataset_path = dataset_path
        self.batch_size = model_cfg.batch_size

        self.max_sequence_length = model_cfg.max_sequence_length
        self.max_num_usages = model_cfg.max_num_usages
        self.max_target_length = model_cfg.max_target_length
        self.use_camel_case_tokenizer = model_cfg.use_camel_case_tokenizer

        # Inits that will be initialized later
        self.train_dataset = None
        self.val_dataset = None
        self.test_dataset = None

        self.usages_field = None
        self.target_field = None
        self.init_token = '<s>'
        self.eos_token = '</s>'

        self.usage_vocab_size = None
        self.target_vocab_size = None
        self.usage_pad_idx = None
        self.target_pad_idx = None

    def setup(self, stage=None):
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
                    if self.split_strategy == "mixed":
                        if len(_dataset) >= 10:
                            _train_files, _test_files = train_test_split(list(_dataset.keys()), train_size=0.8)
                            _test_files, _val_files = train_test_split(_test_files, train_size=0.5)
                            train_files.extend(_train_files)
                            val_files.extend(_val_files)
                            test_files.extend(_test_files)
                        else:
                            test_files.extend(list(_dataset.keys()))
                    elif self.split_strategy == "java-small":
                        if project_name in self.JAVA_SMALL_TRAIN:
                            print("Sending to train...")
                            train_files.extend(_dataset.keys())
                        elif project_name in self.JAVA_SMALL_VAL:
                            print("Sending to val...")
                            val_files.extend(_dataset.keys())
                        elif project_name in self.JAVA_SMALL_TEST:
                            print("Sending to test...")
                            test_files.extend(_dataset.keys())
                        else:
                            print(f"Project {project_name} is not in java-small dataset.")
                    else:
                        raise NotImplementedError()

        self.target_field = data.Field(sequential=True,
                                       use_vocab=True,
                                       init_token=self.init_token,
                                       eos_token=self.eos_token,
                                       fix_length=self.max_target_length,
                                       dtype=torch.long,
                                       tokenize=camelCaseTokenizer(self.use_camel_case_tokenizer),
                                       batch_first=True,
                                       is_target=True,
                                       include_lengths=True)

        # Configuring torchtext fields for automatic padding and numericalization of batches
        self.usages_field = data.NestedField(data.Field(sequential=True,
                                                        use_vocab=True,
                                                        init_token=None,
                                                        eos_token=None,
                                                        fix_length=self.max_sequence_length,
                                                        dtype=torch.long,
                                                        tokenize=reverseSplit,
                                                        batch_first=True,
                                                        is_target=False),
                                             fix_length=self.max_num_usages,
                                             include_lengths=True)

        print("Building train dataset...")
        self.train_dataset = self.make_dataset(train_files, dataset)
        print("Building usage vocab...")
        self.usages_field.build_vocab(self.train_dataset)
        print("Building target vocab...")
        self.target_field.build_vocab(self.train_dataset)
        print("Building val dataset...")
        self.val_dataset = self.make_dataset(val_files, dataset)
        print("Building test dataset...")
        self.test_dataset = self.make_dataset(test_files, dataset)

        self.usage_vocab_size = len(self.usages_field.vocab.itos)
        self.usage_pad_idx = self.usages_field.vocab.stoi['<pad>']
        self.target_vocab_size = len(self.target_field.vocab.itos)
        self.target_pad_idx = self.target_field.vocab.stoi['<pad>']

    def make_dataset(self, files, dset):
        example_fields = {'variable': ('target', self.target_field),
                          'ngrams': ('usages', self.usages_field)}
        dataset_fields = {'target': self.target_field,
                          'usages': self.usages_field}
        dataset = data.Dataset(
            list(tmap(
                lambda d: data.Example.fromdict(d, example_fields),
                chain(*map(lambda file: dset[file], files)),
                mininterval=0.5
            )),
            dataset_fields)
        for file in files:
            del dset[file]
        return dataset

    def train_dataloader(self):
        return data.BucketIterator(self.train_dataset, self.batch_size)

    def val_dataloader(self):
        return data.BucketIterator(self.val_dataset, self.batch_size)

    def test_dataloader(self):
        return data.BucketIterator(self.test_dataset, self.batch_size)

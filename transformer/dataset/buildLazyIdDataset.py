import json
import os
from os.path import join, exists

from tqdm.auto import tqdm

from dataset.lazyIdDataset import LazyIdDataset
from utils import read_json, JAVA_SMALL_TRAIN, JAVA_SMALL_VAL, JAVA_SMALL_TEST


def build_lazy_dataset(dataset_path):
    train_path = join(dataset_path, LazyIdDataset.train_path)
    val_path = join(dataset_path, LazyIdDataset.val_path)
    test_path = join(dataset_path, LazyIdDataset.test_path)
    if exists(train_path) and exists(val_path) and exists(test_path):
        print("Dataset is already built")
        return
    if not exists(os.path.dirname(train_path)):
        os.makedirs(os.path.dirname(train_path))
    open(train_path, "w", encoding='utf-8').close()
    open(val_path, "w", encoding='utf-8').close()
    open(test_path, "w", encoding='utf-8').close()
    for file in os.listdir(dataset_path):
        file_path = join(dataset_path, file)
        if os.path.isfile(file_path) & file.endswith("_dataset.json"):
            project_name = file.replace("_dataset.json", "")
            print(f"Reading dataset {project_name}...")
            dataset = read_json(file_path)
            if project_name in JAVA_SMALL_TRAIN:
                write_dataset(dataset, train_path)
            elif project_name in JAVA_SMALL_VAL:
                write_dataset(dataset, val_path)
            elif project_name in JAVA_SMALL_TEST:
                write_dataset(dataset, test_path)
            else:
                print(f"Project {project_name} is not in java-small dataset.")


def write_dataset(data, path):
    with open(path, "a+", encoding='utf-8') as f:
        for vs in tqdm(data.values()):
            for v in vs:
                if v["variable"] and v["ngrams"]:
                    print(json.dumps(v), file=f)


if __name__ == "__main__":
    build_lazy_dataset(r"C:\Users\Igor\IdeaProjects\ide-plugin\build\idea-sandbox\system\dataset\java-small")

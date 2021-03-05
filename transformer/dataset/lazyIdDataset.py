from os.path import join, exists

from torchtext.data import Dataset, Example, Batch

from dataset.baseIdDataset import BaseIdDataset

from linecache import getline, getlines


class LazyIdDataset(BaseIdDataset):
    train_path = r"train.txt"
    val_path = r"val.txt"
    test_path = r"test.txt"

    def setup(self):
        train_path = join(self.dataset_path, self.train_path)
        val_path = join(self.dataset_path, self.val_path)
        test_path = join(self.dataset_path, self.test_path)

        example_fields = {'variable': ('target', self.target_field),
                          'ngrams': ('usages', self.usages_field)}
        dataset_fields = {'target': self.target_field,
                          'usages': self.usages_field}

        reader_cls = LazyReader if self.lazy_reader_type == "with_cache" else LazyByteReader
        print("Building train dataset...")
        self.train = Dataset(reader_cls(train_path, example_fields), dataset_fields)
        print("Building val dataset...")
        self.val = Dataset(reader_cls(val_path, example_fields), dataset_fields)
        print("Building test dataset...")
        self.test = Dataset(reader_cls(test_path, example_fields), dataset_fields)

    def collate_fn(self, batch):
        return Batch(batch, self.train)


class LazyReader:
    """Uses caching and, hence, lots of RAM (crashes in Google Colab)"""

    def __init__(self, data_path, example_fields):
        if not exists(data_path):
            raise FileNotFoundError(f"File {data_path} is not found")
        self.data_path = data_path
        self.example_fields = example_fields

    def __len__(self):
        return len(getlines(self.data_path))

    def __getitem__(self, index):
        line = self._read_line(index)
        try:
            ex = Example.fromJSON(line, self.example_fields)
            if not ex.target:
                ex.target = ""
            if not ex.usages:
                ex.usages = [""]
            return ex
        except Exception as e:
            print(e)
            print(line)

    def _read_line(self, index):
        line = getline(self.data_path, index + 1).strip()
        if line == "":
            raise RuntimeError(f"Error in getline with params: {self.data_path, index + 1}")
        return line

    def __iter__(self):
        for i in range(len(self)):
            yield self[i]


class LazyByteReader(LazyReader):
    def __init__(self, data_path, example_fields):
        super().__init__(data_path, example_fields)
        cumulative_offset = 0
        self.line_offsets = []
        with open(self.data_path, "br") as file:
            for line in file:
                self.line_offsets.append(cumulative_offset)
                cumulative_offset += len(line)

    def __len__(self):
        return len(self.line_offsets)

    def _read_line(self, index):
        with open(self.data_path, "br") as file:
            file.seek(self.line_offsets[index])
            line = file.readline().decode(encoding="utf-8").strip()
        return line

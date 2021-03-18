import pytorch_lightning as pl
from torch.utils.data import DataLoader
from torchtext.legacy import data

from dataset.jsonIdDataset import JSONIdDataset
from dataset.lazyIdDataset import LazyIdDataset
from utils import INIT_TOKEN, PAD_TOKEN, EOS_TOKEN


class IdDataModule(pl.LightningDataModule):
    def __init__(self, cfg):
        super().__init__()
        self.cfg = cfg
        self.dataset_type = cfg.dataset.type

        self.batch_size = cfg.model.batch_size
        self.dataset = None
        self.trainloader = None
        self.valloader = None
        self.testloader = None

    def setup(self, stage=None):
        if self.dataset_type == "json":
            self.dataset = JSONIdDataset(self.cfg)
            self.trainloader = data.BucketIterator(self.dataset.train,
                                                   batch_size=self.batch_size,
                                                   train=True,
                                                   repeat=True)
            self.valloader = data.BucketIterator(self.dataset.val,
                                                 batch_size=self.batch_size,
                                                 train=False,
                                                 repeat=True)
            self.testloader = data.BucketIterator(self.dataset.val,
                                                  batch_size=self.batch_size,
                                                  train=False,
                                                  repeat=True)
        elif self.dataset_type == "lazy":
            self.dataset = LazyIdDataset(self.cfg)
            self.trainloader = DataLoader(self.dataset.train,
                                          batch_size=self.batch_size,
                                          shuffle=True,
                                          collate_fn=self.dataset.collate_fn)
            self.valloader = DataLoader(self.dataset.val,
                                        batch_size=self.batch_size,
                                        shuffle=False,
                                        collate_fn=self.dataset.collate_fn)
            self.testloader = DataLoader(self.dataset.test,
                                         batch_size=self.batch_size,
                                         shuffle=False,
                                         collate_fn=self.dataset.collate_fn)
        else:
            raise NotImplementedError(f"There is no implementation for {self.dataset_type} dataset type")
        self.dataset.is_training(stage == "train")

    @property
    def usage_vocab_size(self):
        return len(self.dataset.usage_vocabulary)

    @property
    def usage_pad_idx(self):
        return self.dataset.usage_vocabulary[PAD_TOKEN]

    @property
    def target_vocab_size(self):
        return len(self.dataset.target_vocabulary)

    @property
    def target_pad_idx(self):
        return self.dataset.target_vocabulary[PAD_TOKEN]

    @property
    def target_init_idx(self):
        return self.dataset.target_vocabulary[INIT_TOKEN]

    @property
    def target_eos_idx(self):
        return self.dataset.target_vocabulary[EOS_TOKEN]

    def train_dataloader(self):
        return self.trainloader

    def val_dataloader(self):
        return self.valloader

    def test_dataloader(self):
        return self.testloader

    def prepare_data(self, *args, **kwargs):
        pass

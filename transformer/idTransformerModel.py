import pytorch_lightning as pl
import torch
import torch.nn as nn
from torchtext import data

from sklearn.model_selection import train_test_split

from idDecoder import IdDecoder
from idEncoder import IdEncoder
from utils import top_predictions, read_json, camelCaseTokenizer, reverseSplit

from metrics import Top5, MRR


# from pytorch_lightning.loggers import WandbLogger
# WandbLogger()

class IdTransformerModel(pl.LightningModule):

    def __init__(self,
                 dataset_path,
                 max_sequence_length,
                 max_num_usages,
                 max_target_length,
                 embedding_dim,
                 usage_embedding_dim,
                 target_embedding_dim,
                 num_heads,
                 num_encoder_layers,
                 num_decoder_layers,
                 num_usage_layers=1,
                 dropout=0.1,
                 lr=1e-3,
                 batch_size=8,
                 sequence_encoder_type='transformer',
                 **kargs):
        """
        A model that predicts the name of a variable using its usages.

        :param dataset_path: the dataset path,
        :param max_sequence_length: the order of usage ngrams,
        :param max_num_usages: the max number of usages to use,
        :param max_target_length: the max length of target sequence,
        :param embedding_dim: the dimension of token embedding,
        :param usage_embedding_dim: the dimension of usage embedding,
        :param target_embedding_dim: the dimension of target token embedding,
        :param num_heads: the number of heads in multiHeadAttention,
        :param num_encoder_layers: the number of sub-encoder-layers,
        :param num_decoder_layers: the number of sub-decoder-layers,
        :param num_usage_layers: the number of sub-usage-layers,
        :param dropout: the dropout value,
        :param lr: the learning rate,
        :param batch_size: the batch size of loaders.
        """
        super(IdTransformerModel, self).__init__()

        self.max_sequence_length = max_sequence_length
        self.max_num_usages = max_num_usages
        self.max_target_length = max_target_length
        self.embedding_dim = embedding_dim
        self.usage_embedding_dim = usage_embedding_dim
        self.target_embedding_dim = target_embedding_dim
        self.num_heads = num_heads
        self.num_encoder_layers = num_encoder_layers
        self.num_decoder_layers = num_decoder_layers
        self.num_usage_layers = num_usage_layers
        self.dropout = dropout
        self.sequence_encoder_type = sequence_encoder_type

        self.dataset_path = dataset_path
        self.batch_size = batch_size
        self.lr = lr

        self.usages_field = None
        self.target_field = None
        self.init_token = "<s>"
        self.eos_token = "</s>"
        self.usage_vocab_size = None
        self.target_vocab_size = None
        self.usage_pad_idx = None
        self.target_pad_idx = None

        self.trainloader = None
        self.valloader = None
        self.testloader = None

        self.encoder = None
        self.decoder = None

        self.train_top1 = pl.metrics.Accuracy(compute_on_step=False)
        self.train_top5 = Top5()
        self.train_mrr = MRR()

        self.val_top1 = pl.metrics.Accuracy(compute_on_step=False)
        self.val_top5 = Top5()
        self.val_mrr = MRR()

        self.test_top1 = pl.metrics.Accuracy(compute_on_step=False)
        self.test_top5 = Top5()
        self.test_mrr = MRR()

        self.loss = nn.CrossEntropyLoss()

    def forward(self, src):
        memory = self.encoder.forward(src, None)
        b, *_ = src.shape
        tgt = torch.ones(b, 1, device=src.device, dtype=torch.long) * self.target_field.vocab.stoi['<s>']
        out = self.decoder.forward(memory, tgt, None, None)
        return out

    def training_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('train_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.train_top1.update(predictions[:, 0], targets)
        self.train_top5.update(predictions, targets)
        self.train_mrr.update(predictions, targets)
        metrics = {"train_top1": self.train_top1,
                   "train_top5": self.train_top5,
                   "train_mrr": self.train_mrr}
        self.log_dict(metrics, on_step=False, on_epoch=True, prog_bar=False)
        return loss

    def validation_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('val_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.val_top1.update(predictions[:, 0], targets)
        self.val_top5.update(predictions, targets)
        self.val_mrr.update(predictions, targets)
        return loss

    def on_validation_epoch_end(self, *args, **kwargs):
        metrics = {"val_top1": self.val_top1,
                   "val_top5": self.val_top5,
                   "val_mrr": self.val_mrr}
        self.log_dict(metrics, on_step=False, on_epoch=True, prog_bar=False)

    def test_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('test_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.test_top1.update(predictions[:, 0], targets)
        self.test_top5.update(predictions, targets)
        self.test_mrr.update(predictions, targets)
        metrics = {"test_top1": self.test_top1,
                   "test_top5": self.test_top5,
                   "test_mrr": self.test_mrr}
        self.log_dict(metrics, on_step=False, on_epoch=True, prog_bar=False)

    def step(self, batch, batch_idx):
        src, tgt = batch
        x, num_usages, _ = src
        tgt, tgt_length = tgt
        memory = self.encoder.forward(x, num_usages)
        out = self.decoder.forward(memory, tgt, num_usages, tgt_length)
        tgt_to_loss = torch.constant_pad_nd(tgt, (0, 1), self.target_pad_idx)[..., 1:]  # BxT
        # [[<START>, <TOKEN>, <END>, <PAD>]] -> [[<TOKEN>, <END>, <PAD>, <PAD>]]
        out_to_loss = out.transpose(1, 2)  # BxTxV -> BxVxT
        with torch.no_grad():
            predictions = out_to_loss[..., 0]  # BxV takes first out of transformer
            predictions = top_predictions(predictions, n=10)  # BxV -> Bx10
            targets = tgt[:, 1]  # B takes variable token
        return self.loss(out_to_loss, tgt_to_loss), predictions, targets

    def configure_optimizers(self):
        return torch.optim.Adam(self.parameters(), lr=self.lr)

    def setup(self, stg):
        # Reading dataset
        dataset = read_json(self.dataset_path)

        # Splitting datasets by files
        train_files, test_files = train_test_split(list(dataset.keys()), train_size=0.8)
        test_files, val_files = train_test_split(test_files, train_size=0.5)

        def make_dataset(files, dset):
            return sum([dset[file] for file in files], [])

        train = make_dataset(train_files, dataset)
        test = make_dataset(test_files, dataset)
        val = make_dataset(val_files, dataset)

        # Configuring torchtext fields for automatic padding and numericalization of batches
        self.target_field = data.Field(sequential=True,
                                       use_vocab=True,
                                       init_token=self.init_token,
                                       eos_token=self.eos_token,
                                       fix_length=self.max_target_length,
                                       dtype=torch.long,
                                       tokenize=camelCaseTokenizer(False),
                                       batch_first=True,
                                       is_target=True,
                                       include_lengths=True)
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
        example_fields = {'variable': ('target', self.target_field),
                          'ngrams': ('usages', self.usages_field)}
        dataset_fields = {'target': self.target_field,
                          'usages': self.usages_field}

        train_dataset = data.Dataset(list(map(lambda d: data.Example.fromdict(d, example_fields), train)),
                                     dataset_fields)
        self.target_field.build_vocab(train_dataset)
        self.usages_field.build_vocab(train_dataset)
        test_dataset = data.Dataset(list(map(lambda d: data.Example.fromdict(d, example_fields), test)), dataset_fields)
        val_dataset = data.Dataset(list(map(lambda d: data.Example.fromdict(d, example_fields), val)), dataset_fields)

        # Storing loaders
        self.trainloader = data.BucketIterator(train_dataset, self.batch_size)
        self.testloader = data.BucketIterator(test_dataset, self.batch_size)
        self.valloader = data.BucketIterator(val_dataset, self.batch_size)

        self.usage_vocab_size = len(self.usages_field.vocab.itos)
        self.usage_pad_idx = self.usages_field.vocab.stoi['<pad>']
        self.target_vocab_size = len(self.target_field.vocab.itos)
        self.target_pad_idx = self.target_field.vocab.stoi['<pad>']

        # Initialization of encoder and decoder
        self.encoder = IdEncoder(self.max_sequence_length,
                                 self.max_num_usages,
                                 self.usage_vocab_size,
                                 self.usage_pad_idx,
                                 self.embedding_dim,
                                 self.num_heads,
                                 self.usage_embedding_dim,
                                 self.num_encoder_layers,
                                 self.num_usage_layers,
                                 self.dropout,
                                 self.sequence_encoder_type)
        self.decoder = IdDecoder(self.max_num_usages,
                                 self.max_target_length,
                                 self.target_vocab_size,
                                 self.target_pad_idx,
                                 self.usage_embedding_dim,
                                 self.num_heads,
                                 self.target_embedding_dim,
                                 self.num_decoder_layers,
                                 self.dropout)

    def train_dataloader(self):
        return self.trainloader

    def val_dataloader(self):
        return self.valloader

    def test_dataloader(self):
        return self.testloader

import pytorch_lightning as pl
import torch
import torch.nn as nn

from idDecoder import IdDecoder
from idEncoder import IdEncoder
from metrics import Top5, MRR
from utils import top_predictions


class IdTransformerModel(pl.LightningModule):

    def __init__(self, dm, cfg):
        """
        A model that predicts the name of a variable using its usages.

        :param dm: datamodule which contains train, val, test datasets,
        :param cfg: config that contains all needed parameters.
        """
        super(IdTransformerModel, self).__init__()

        self.dm = dm

        self.max_sequence_length = cfg.max_sequence_length
        self.max_num_usages = cfg.max_num_usages
        self.max_target_length = cfg.max_target_length
        self.embedding_dim = cfg.embedding_dim
        self.usage_embedding_dim = cfg.usage_embedding_dim
        self.target_embedding_dim = cfg.target_embedding_dim
        self.num_heads = cfg.num_heads
        self.num_encoder_layers = cfg.num_encoder_layers
        self.num_decoder_layers = cfg.num_decoder_layers
        self.num_usage_layers = cfg.num_usage_layers
        self.dropout = cfg.dropout
        self.sequence_encoder_type = cfg.sequence_encoder_type

        self.use_camel_case_tokenizer = cfg.use_camel_case_tokenizer
        self.batch_size = cfg.batch_size
        self.lr = cfg.lr

        self.encoder = IdEncoder(dm.max_sequence_length,
                                 dm.max_num_usages,
                                 dm.usage_vocab_size,
                                 dm.usage_pad_idx,
                                 self.embedding_dim,
                                 self.num_heads,
                                 self.usage_embedding_dim,
                                 self.num_encoder_layers,
                                 self.num_usage_layers,
                                 self.dropout,
                                 self.sequence_encoder_type)
        self.decoder = IdDecoder(dm.max_num_usages,
                                 dm.max_target_length,
                                 dm.target_vocab_size,
                                 dm.target_pad_idx,
                                 self.usage_embedding_dim,
                                 self.num_heads,
                                 self.target_embedding_dim,
                                 self.num_decoder_layers,
                                 self.dropout)

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
        tgt = torch.ones(b, 1, device=src.device, dtype=torch.long) * self.dm.target_field.vocab.stoi['<s>']
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
        tgt_to_loss = torch.constant_pad_nd(tgt, (0, 1), self.dm.target_pad_idx)[..., 1:]  # BxT
        # [[<START>, <TOKEN>, <END>, <PAD>]] -> [[<TOKEN>, <END>, <PAD>, <PAD>]]
        out_to_loss = out.transpose(1, 2)  # BxTxV -> BxVxT
        with torch.no_grad():
            predictions = out_to_loss[..., 0]  # BxV takes first out of transformer
            predictions = top_predictions(predictions, n=10)  # BxV -> Bx10
            targets = tgt[:, 1]  # B takes variable token
        return self.loss(out_to_loss, tgt_to_loss), predictions, targets

    def configure_optimizers(self):
        return torch.optim.Adam(self.parameters(), lr=self.lr)

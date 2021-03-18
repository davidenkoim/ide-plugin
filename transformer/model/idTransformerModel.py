import pytorch_lightning as pl
import torch
import torch.nn as nn
from pytorch_lightning.metrics import MetricCollection

from metrics import Top, MRR, Accuracy, Precision, Recall, F1
from model.idDecoder import IdDecoder
from model.idEncoder import IdEncoder


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

        self.batch_size = cfg.batch_size
        self.max_lr = cfg.max_lr

        self.encoder = IdEncoder(self.max_sequence_length,
                                 self.max_num_usages,
                                 self.dm.usage_vocab_size,
                                 self.dm.usage_pad_idx,
                                 self.embedding_dim,
                                 self.num_heads,
                                 self.usage_embedding_dim,
                                 self.num_encoder_layers,
                                 self.num_usage_layers,
                                 self.dropout,
                                 self.sequence_encoder_type)
        self.decoder = IdDecoder(self.max_num_usages,
                                 self.max_target_length,
                                 self.dm.target_vocab_size,
                                 self.dm.target_pad_idx,
                                 self.usage_embedding_dim,
                                 self.num_heads,
                                 self.target_embedding_dim,
                                 self.num_decoder_layers,
                                 self.dropout)

        self.tusk = "generation" if cfg.target_tokenizer_type is not None else "classification"
        self.train_metrics = MetricCollection(self.metrics_dict("train"))
        self.val_metrics = MetricCollection(self.metrics_dict("val"))
        self.test_metrics = MetricCollection(self.metrics_dict("test"))

        self.loss = nn.CrossEntropyLoss()

    def metrics_dict(self, prefix="train"):
        if self.tusk == "classification":
            return {f"{prefix}_top1": Top(n=1),
                    f"{prefix}_top5": Top(n=5),
                    f"{prefix}_MRR": MRR()}
        elif self.tusk == "generation":
            ignore_idxs = (self.dm.target_eos_idx, self.dm.target_pad_idx)
            return {f"{prefix}_accuracy": Accuracy(),
                    f"{prefix}_precision": Precision(ignore_idxs),
                    f"{prefix}_recall": Recall(ignore_idxs),
                    f"{prefix}_F1": F1(ignore_idxs)}
        else:
            return ValueError(f"{self.tusk} tusk is not supported")

    def forward(self, usages, num_usages=None, beam_search=True, limit_num_usages=10, **kwargs):
        inp = self.dm.dataset.input_from_usages(usages, limit_num_usages=limit_num_usages, device=self.device)
        if len(inp.shape) != 3:
            raise ValueError("Source shape must be equal 3")
        with torch.no_grad():
            memory = self.encoder(inp, num_usages)
            if beam_search:
                out = self.decoder.beam_search(memory, self.max_target_length,
                                               self.dm.target_init_idx,
                                               self.dm.target_eos_idx, **kwargs)
            else:
                out = self.decoder.greedy_search(memory, self.max_target_length,
                                                 self.dm.target_init_idx,
                                                 self.dm.target_eos_idx)
        return out

    def training_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('train_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.train_metrics.update(predictions, targets)
        self.log_dict(self.train_metrics, on_step=False, on_epoch=True, prog_bar=False)
        return loss

    def validation_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('val_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.val_metrics.update(predictions, targets)
        self.log_dict(self.val_metrics, on_step=False, on_epoch=True, prog_bar=False)
        return loss

    def test_step(self, batch, batch_idx):
        loss, predictions, targets = self.step(batch, batch_idx)
        self.log('test_loss', loss, prog_bar=True, on_step=True, on_epoch=True)
        self.test_metrics.update(predictions, targets)
        self.log_dict(self.test_metrics, on_step=False, on_epoch=True, prog_bar=False)

    def step(self, batch, batch_idx):
        src, tgt = batch
        x, num_usages, _ = src
        tgt, tgt_length = tgt
        memory = self.encoder(x, num_usages)
        out = self.decoder(memory, tgt, num_usages, tgt_length)
        tgt_to_loss = torch.constant_pad_nd(tgt, (0, 0, 0, 1), self.dm.target_pad_idx)[..., 1:]  # BxT
        # [[<s>], [<token>], [</s>], [<pad>]]] -> [[<token>], [</s>], [<pad>], [<pad>]]
        out_to_loss = out.transpose(1, 2)  # TxBxV -> TxVxB
        return self.loss(out_to_loss, tgt_to_loss), out, tgt

    def configure_optimizers(self):
        def schedule(step, warmup_steps=len(self.dm.trainloader)):
            x = (step + 1) / warmup_steps
            return min(1, x ** (-0.5))

        optimizer = torch.optim.Adam(self.parameters(), lr=self.max_lr)
        scheduler = torch.optim.lr_scheduler.LambdaLR(optimizer, schedule)
        return {
            "optimizer": optimizer,
            'scheduler': scheduler,
            'interval': 'step'
        }

import logging
import warnings

import hydra
import pytorch_lightning as pl
from pytorch_lightning.callbacks import ModelCheckpoint
from pytorch_lightning.loggers import WandbLogger

from dataset.idDataModule import IdDataModule
from model.idTransformerModel import IdTransformerModel

import time

warnings.filterwarnings("ignore")


@hydra.main(config_path='configs', config_name='config')
def train(cfg):
    name_of_run = cfg.model.name_of_run
    logger = True
    if cfg.show_in_wandb:
        logger = logging.getLogger("wandb")
        logger.setLevel(logging.WARNING)
        # Initializing wandb logger.
        logger = WandbLogger(name=name_of_run, project='IdTransformer', log_model=True)
        logger.log_hyperparams(cfg)

    datamodule = IdDataModule(cfg)
    datamodule.setup('fit')
    model = IdTransformerModel(datamodule, cfg.model)

    if cfg.show_in_wandb:
        logger.watch(model, log='all')
    monitor_metric = "val_top1" if model.tusk == "classification" else "val_accuracy"
    t = time.gmtime()
    checkpoint_callback = ModelCheckpoint(save_last=True,
                                          save_top_k=3,
                                          mode="max",
                                          monitor=monitor_metric,
                                          dirpath=cfg.model.checkpoints_dir,
                                          filename=f"{t.tm_mday:02}.{t.tm_mon:02}-{name_of_run}-{cfg.dataset.name}-{{epoch:02d}}-{{{monitor_metric}:.2f}}")
    trainer = pl.Trainer(max_epochs=cfg.model.max_epochs,
                         logger=logger,
                         gpus=cfg.model.gpus,
                         weights_summary='top',
                         callbacks=[checkpoint_callback],
                         log_every_n_steps=10,
                         val_check_interval=0.5,
                         resume_from_checkpoint=None)
    trainer.fit(model, datamodule=datamodule)


if __name__ == "__main__":
    train()

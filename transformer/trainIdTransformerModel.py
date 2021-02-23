import hydra
import pytorch_lightning as pl
from pytorch_lightning.callbacks import ModelCheckpoint
from pytorch_lightning.loggers import WandbLogger

from idTransformerModel import IdTransformerModel
from idDataModule import IdDataModule
import logging


@hydra.main(config_path='configs', config_name='config')
def train(cfg):
    datamodule = IdDataModule(cfg.dataset_path, cfg.dataset_split_strategy, cfg.model)
    datamodule.setup('fit')
    model = IdTransformerModel(datamodule, cfg.model)
    name_of_run = cfg.model.name_of_run

    logger = True
    if cfg.show_in_wandb:
        # Turn off wandb warnings.
        logger = logging.getLogger("wandb")
        logger.setLevel(logging.WARNING)
        # Initializing wandb logger.
        logger = WandbLogger(name=name_of_run, project='IdTransformer', log_model=True)
        logger.log_hyperparams(cfg.model)
        logger.watch(model, log='all', log_freq=50)
    checkpoint_callback = ModelCheckpoint(save_last=True,
                                          save_top_k=5,
                                          monitor='val_loss',
                                          dirpath=cfg.checkpoints_dir,
                                          filename=name_of_run + '-{epoch:02d}-{val_mrr:.2f}')
    trainer = pl.Trainer(max_epochs=cfg.model.max_epochs,
                         logger=logger,
                         gpus=cfg.model.gpus,
                         weights_summary='full',
                         callbacks=[checkpoint_callback])
    trainer.fit(model, datamodule)


if __name__ == "__main__":
    train()

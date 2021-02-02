import hydra
import pytorch_lightning as pl
from pytorch_lightning.callbacks import ModelCheckpoint
from pytorch_lightning.loggers import WandbLogger

from idTransformerModel import IdTransformerModel


@hydra.main(config_path='configs', config_name='test_config')
def train(cfg):
    model = IdTransformerModel(**cfg)

    wandb_logger = WandbLogger(name=cfg.name_of_run, project='IdTransformer')
    wandb_logger.log_hyperparams(cfg)
    wandb_logger.watch(model, log='gradients', log_freq=100)
    checkpoint_callback = ModelCheckpoint(save_last=True,
                                          save_top_k=5,
                                          monitor='val_loss',
                                          dirpath='checkpoints/',
                                          filename=str(cfg.name_of_run) + '-{epoch:02d}-{val_mrr:.2f}')
    trainer = pl.Trainer(max_epochs=cfg.max_epochs,
                         logger=wandb_logger,
                         gpus=cfg.gpus,
                         weights_summary='full',
                         callbacks=[checkpoint_callback])
    trainer.fit(model)


if __name__ == "__main__":
    train()

import json
import time
from collections import namedtuple
from os.path import join

from flask import Flask
from flask import request
from flask_ngrok import run_with_ngrok
from omegaconf import OmegaConf

from dataset.idDataModule import IdDataModule
from model.idTransformerModel import IdTransformerModel

app = Flask(__name__)
run_with_ngrok(app)
DEVICE = "cpu"

def load_model(configs_path=r'C:/Users/Igor/IdeaProjects/ide-plugin/transformer/configs/'):
    cfg = OmegaConf.load(join(configs_path, 'config.yaml'))
    cfg['model'] = OmegaConf.load(join(configs_path, r'model/transformer_encoder.yaml'))
    cfg['dataset'] = OmegaConf.load(join(configs_path, r'dataset/java-small.yaml'))
    datamodule = IdDataModule(cfg)
    datamodule.setup('test')
    return IdTransformerModel.load_from_checkpoint(
        r"C:\Users\Igor\IdeaProjects\ide-plugin\transformer\checkpoints\11.03-transformer_encoder-java-small-epoch=02-val_accuracy=0.19.ckpt",
        map_location=DEVICE,
        dm=datamodule,
        cfg=cfg.model)


model = load_model(configs_path=r'C:/Users/Igor/IdeaProjects/ide-plugin/transformer/configs/')
model.train(False)
model.dm.dataset.is_training(False)
LastInference = namedtuple("LastInference", ["predictions", "gt", "time_spent"])
last_inference = LastInference(None, None, None)


@app.route('/evaluate', methods=['POST'])
def evaluate():
    if request.method == 'POST':
        start = time.perf_counter()
        variable_features = request.get_json(force=True, cache=False)
        usages = variable_features["ngrams"]
        inp = model.dm.dataset.input_from_usages(usages, limit_num_usages=10, device=DEVICE)
        predictions = model(inp)
        predictions = list(map(lambda prediction:
                               {
                                   "name": model.dm.dataset.ids_to_tokens(prediction[1].token_ids_array)[1:-1],
                                   "probability": prediction[1].p
                               }, predictions))
        time_spent = time.perf_counter() - start
        global last_inference
        predictions = json.dumps(predictions)
        last_inference = LastInference(predictions, variable_features["variable"], time_spent)
        return predictions


@app.route('/')
def running():
    global last_inference
    return "I don't have anything to show!" if last_inference.gt is None \
        else f"Running!<p>Last inference:<p>Time spent: {last_inference.time_spent}<p>" + \
             f"<p>Ground truth: {last_inference.gt}<p>" + \
             f"Predictions: {last_inference.predictions}"

from abc import abstractmethod

import torch
from pytorch_lightning.metrics import Metric


class ClassificationMetric(Metric):
    def __init__(self, compute_on_step=False):
        super().__init__(compute_on_step=compute_on_step)

        self.add_state("correct", default=torch.tensor(0), dist_reduce_fx="sum")
        self.add_state("total", default=torch.tensor(0), dist_reduce_fx="sum")

    def update(self, predictions, target):
        """
        :param predictions: TxBxV
        :param target: TxB
        :return:
        """
        assert predictions.shape[:2] == target.shape[:2]
        tgts = target[1].unsqueeze(1)  # TxB -> Bx1
        preds = torch.argsort(predictions[0, :, :], dim=-1, descending=True)[:, :10]  # TxBxV -> BxN

        ranks = torch.nonzero(torch.eq(preds, tgts))[:, 1]
        self.correct += torch.sum(self.analyze_ranks(ranks))
        self.total += tgts.numel()

    def compute(self):
        return self.correct.float() / self.total

    @abstractmethod
    def analyze_ranks(self, ranks):
        pass


class Top(ClassificationMetric):
    def __init__(self, n):
        super().__init__()
        self.n = n

    def analyze_ranks(self, ranks):
        return ranks < self.n


class MRR(ClassificationMetric):
    def analyze_ranks(self, ranks):
        return 1 / (ranks + 1)


class GenerationMetric(Metric):
    def __init__(self, ignore_idxs=(), compute_on_step=False):
        super().__init__(compute_on_step=compute_on_step)

        self.ignore_idxs = ignore_idxs
        self.add_state("tp", default=torch.tensor(0), dist_reduce_fx="sum")
        self.add_state("fp", default=torch.tensor(0), dist_reduce_fx="sum")
        self.add_state("fn", default=torch.tensor(0), dist_reduce_fx="sum")

    def update(self, predictions, target):
        """
        :param predictions: TxBxV
        :param target: TxB
        :return:
        """
        assert predictions.shape[:2] == target.shape[:2]
        preds = torch.argmax(predictions[:-1], dim=-1).transpose(0, 1)  # TxBxV -> Bx(T-1)
        tgts = target[1:].transpose(0, 1)  # TxB -> Bx(T-1)

        for pred, tgt in zip(preds, tgts):
            for pred_subtoken in filter(lambda x: x not in self.ignore_idxs, pred):
                if pred_subtoken in tgt:
                    self.tp += 1
                else:
                    self.fp += 1
            for tgt_subtoken in filter(lambda x: x not in self.ignore_idxs, tgt):
                if tgt_subtoken not in pred:
                    self.fn += 1

    @abstractmethod
    def compute(self):
        pass


class Precision(GenerationMetric):
    def compute(self):
        return self.tp / (self.tp + self.fp)


class Recall(GenerationMetric):
    def compute(self):
        return self.tp / (self.tp + self.fn)


class F1(GenerationMetric):
    def compute(self):
        precision, recall = self.compute_precision(), self.compute_recall()
        return 2 * precision * recall / (precision + recall)

    def compute_precision(self):
        return self.tp / (self.tp + self.fp)

    def compute_recall(self):
        return self.tp / (self.tp + self.fn)


class Accuracy(Metric):
    def __init__(self, compute_on_step=False):
        super().__init__(compute_on_step=compute_on_step)

        self.add_state("correct", default=torch.tensor(0), dist_reduce_fx="sum")
        self.add_state("total", default=torch.tensor(0), dist_reduce_fx="sum")

    def update(self, predictions, target):
        """
        :param predictions: TxBxV
        :param target: TxB
        :return:
        """
        assert predictions.shape[:2] == target.shape[:2]
        preds = torch.argmax(predictions[:-1], dim=-1).transpose(0, 1)  # TxBxV -> Bx(T-1)
        tgts = target[1:].transpose(0, 1)  # TxB -> Bx(T-1)

        self.correct += torch.sum(torch.eq(preds, tgts).all(dim=1))
        self.total += tgts.shape[0]

    def compute(self):
        return self.correct.float() / self.total

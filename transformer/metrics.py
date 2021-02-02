from abc import abstractmethod

from pytorch_lightning.metrics import Metric
import torch


class MyMetric(Metric):
    def __init__(self, compute_on_step=False):
        super().__init__(compute_on_step=compute_on_step)

        self.add_state("correct", default=torch.tensor(0.), dist_reduce_fx="sum")
        self.add_state("total", default=torch.tensor(0), dist_reduce_fx="sum")

    def update(self, predictions, target):
        """
        :param predictions: BxN
        :param target: B
        :return:
        """
        assert predictions.shape[0] == target.shape[0]
        target = target.view(-1, 1)  # B -> Bx1

        ranks = torch.nonzero(predictions == target)[:, 1]
        self.correct += torch.sum(self.analyze_ranks(ranks))
        self.total += target.numel()

    def compute(self):
        return self.correct.float() / self.total

    @abstractmethod
    def analyze_ranks(self, ranks):
        pass


class Top5(MyMetric):
    def analyze_ranks(self, ranks):
        return ranks < 5


class MRR(MyMetric):
    def analyze_ranks(self, ranks):
        return 1 / (ranks + 1)

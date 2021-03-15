import torch.nn as nn
from torch.nn import TransformerEncoder, TransformerEncoderLayer

from model.positionalEncoder import PositionalEncoding
from utils import generate_padding_mask


class IdEncoder(nn.Module):
    def __init__(self,
                 max_sequence_length,
                 max_num_usages,
                 vocab_size,
                 pad_idx,
                 embedding_dim,
                 num_heads,
                 hidden_dim,
                 num_layers,
                 num_usage_layers=1,
                 dropout=0.1,
                 sequence_encoder_type='vanilla'):
        """
        Encodes sequence of usages to unconditioned vectors.

        :param max_sequence_length: the order of usage ngrams,
        :param max_num_usages: the max number of usages to use,
        :param vocab_size: the usage vocabulary size,
        :param pad_idx: the index of padding token in usage vocabulary,
        :param embedding_dim: the dimension of token embedding,
        :param num_heads: the number of heads in multiHeadAttention,
        :param hidden_dim: the dimension of the feedforward network after multiHeadAttention,
        :param num_layers: the number of sub-encoder-layers in the sequenceEncoder,
        :param num_usage_layers: the number of sub-encoder-layers in the usageEncoder,
        :param dropout: the dropout value,
        :param sequence_encoder_type: 'vanilla' or 'transformer'.
        """
        super(IdEncoder, self).__init__()
        if sequence_encoder_type == 'transformer':
            self.sequence_encoder = SequenceEncoder(max_sequence_length=max_sequence_length,
                                                    vocab_size=vocab_size,
                                                    pad_idx=pad_idx,
                                                    embedding_dim=embedding_dim,
                                                    num_heads=num_heads,
                                                    hidden_dim=hidden_dim,
                                                    num_layers=num_layers,
                                                    dropout=dropout)
        elif sequence_encoder_type == 'vanilla':
            self.sequence_encoder = VanillaSequenceEncoder(vocab_size=vocab_size,
                                                           pad_idx=pad_idx,
                                                           embedding_dim=embedding_dim)
        else:
            raise NotImplementedError()
        self.usage_encoder = UsageEncoder(
            max_num_usages=max_num_usages,
            embedding_dim=embedding_dim if sequence_encoder_type == 'vanilla' else hidden_dim,
            num_heads=num_heads,
            hidden_dim=hidden_dim,
            num_layers=num_usage_layers,
            dropout=dropout
        )

    def forward(self, x, num_usages=None):
        """
        :param x: BxUxL,
        :param num_usages: B
        :return: UxBxH -- memory that will be fed to decoder
        """
        t = x.transpose(0, 1).contiguous()  # BxUxL -> UxBxL
        t = self.sequence_encoder(t)  # UxBxL -> UxBxE
        t = self.usage_encoder(t, num_usages)  # UxBxE -> UxBxH
        return t  # UxBxH memory that will be fed to decoder


class SequenceEncoder(nn.Module):
    def __init__(self,
                 max_sequence_length,
                 vocab_size,
                 pad_idx,
                 embedding_dim,
                 num_heads,
                 hidden_dim,
                 num_layers,
                 dropout=0.1):
        """
        Encodes ngram sequences of usages to vectors.

        :param max_sequence_length: the order of usage ngrams,
        :param vocab_size: the usages vocabulary size,
        :param pad_idx: the index of padding token in usage vocabulary,
        :param embedding_dim: the dimension of token embedding,
        :param num_heads: the number of heads in multiHeadAttention,
        :param hidden_dim: the dimension of the feedforward network after multiHeadAttention,
        :param num_layers: the number of sub-encoder-layers,
        :param dropout: the dropout value.
        """
        super(SequenceEncoder, self).__init__()
        self.pad_idx = pad_idx
        self.embedding = nn.Embedding(vocab_size, embedding_dim, padding_idx=pad_idx)
        self.positional_encoder = PositionalEncoding(embedding_dim, dropout)
        encoder_layer = TransformerEncoderLayer(embedding_dim, num_heads, hidden_dim, dropout)
        self.encoder = TransformerEncoder(encoder_layer, num_layers)
        self.fc = nn.Linear(hidden_dim * max_sequence_length, hidden_dim)

    def forward(self, x):  # UxBxL
        """
        :param x: UxBxL
        :return: UxBxH
        """
        u, b, l = x.shape
        t = x.view(u * b, l)  # UxBxL -> UBxL
        mask = t == self.pad_idx  # Do not want attention to learn on padding usages
        mask[:, 0] = False  # To handle nans
        t = t.transpose(0, 1)  # UBxL -> LxUB
        t = self.embedding(t)  # LxUB -> LxUBxE
        t = self.positional_encoder(t)
        t = self.encoder(t, src_key_padding_mask=mask)  # LxUBxE -> LxUBxH unconditioned sequence
        h = t.shape[-1]
        t = t.transpose(0, 1).contiguous().view(u * b, l * h)  # LxUBxH -> UBxLH
        t = self.fc(t)  # UBxLH -> UBxH
        return t.view(u, b, h)  # UBxH -> UxBxH


class VanillaSequenceEncoder(nn.Module):
    def __init__(self, vocab_size, pad_idx, embedding_dim):
        super(VanillaSequenceEncoder, self).__init__()
        self.pad_idx = pad_idx
        self.embedding = nn.Embedding(vocab_size, embedding_dim, padding_idx=pad_idx)

    def forward(self, x):  # UxBxL
        """
        :param x: UxBxL
        :return: UxBxE
        """
        t = self.embedding(x)  # UxBxL -> UxBxLxE
        return t.mean(dim=2)  # UxBxLxE -> UxBxE


class UsageEncoder(nn.Module):
    def __init__(self,
                 max_num_usages,
                 embedding_dim,
                 num_heads,
                 hidden_dim,
                 num_layers=1,
                 dropout=0.1):
        """

        :param max_num_usages: the max number of usages to use,
        :param embedding_dim: the dimension of usage embedding,
        :param num_heads: the number of heads in multiHeadAttention,
        :param hidden_dim: the dimension of the feedforward network after multiHeadAttention,
        :param num_layers: the number of sub-encoder-layers,
        :param dropout: the dropout value.
        """
        super(UsageEncoder, self).__init__()
        self.max_num_usages = max_num_usages
        self.positional_encoder = PositionalEncoding(embedding_dim, dropout)
        encoder_layer = TransformerEncoderLayer(embedding_dim, num_heads, hidden_dim, dropout)
        self.encoder = TransformerEncoder(encoder_layer, num_layers)

    def forward(self, x, num_usages=None):
        """
        :param x: UxBxE,
        :param num_usages: B
        :return: UxBxH - unconditioned sequence of usage embeddings
        """
        t = self.positional_encoder(x)  # UxBxE -> UxBxE
        # Do not want padding usages to participate in attention
        padding_mask = generate_padding_mask(num_usages, self.max_num_usages, device=t.device)
        t = self.encoder(t, src_key_padding_mask=padding_mask)  # UxBxE -> UxBxH
        return t  # UxBxH

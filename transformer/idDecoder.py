import torch.nn as nn
from torch.nn import TransformerDecoderLayer, TransformerDecoder

from positionalEncoder import PositionalEncoding
from utils import generate_padding_mask, generate_attention_mask


class IdDecoder(nn.Module):
    def __init__(self,
                 max_memory_length,
                 max_tgt_length,
                 vocab_size,
                 pad_idx,
                 embedding_dim,
                 num_heads,
                 hidden_dim,
                 num_layers,
                 dropout=0.1):
        """
        Decodes target sequence using memory from idEncoder.

        :param max_memory_length: the max number of vectors that is used in memory,
        :param max_tgt_length: the max length of target sequence,
        :param vocab_size: the target vocabulary size,
        :param pad_idx: the index of padding token in target vocabulary,
        :param embedding_dim: the dimension of target token embedding,
        :param num_heads: the number of heads in multiHeadAttention,
        :param hidden_dim: the dimension of the feedforward network after multiHeadAttention,
        :param num_layers: the number of sub-decoder-layers,
        :param dropout: the dropout value.
        """
        super(IdDecoder, self).__init__()
        self.max_memory_length = max_memory_length
        self.max_tgt_length = max_tgt_length
        self.pad_idx = pad_idx
        self.embedding = nn.Embedding(vocab_size, embedding_dim, padding_idx=pad_idx)
        self.positional_encoder = PositionalEncoding(embedding_dim, dropout)
        decoder_layer = TransformerDecoderLayer(embedding_dim, num_heads, hidden_dim, dropout)
        self.decoder = TransformerDecoder(decoder_layer, num_layers)
        self.fc = nn.Linear(hidden_dim, vocab_size)

    def forward(self, memory, tgt, num_memories=None, tgt_length=None):
        """
        :param memory: MxBxE,
        :param tgt: BxT,
        :param num_memories: B,
        :param tgt_length: B
        :return: BxTxV
        """
        t = self.get_embeddings(tgt)  # BxT -> TxBxE
        t = self.decode(memory, t, num_memories, tgt_length)  # TxBxE -> TxBxH
        t = self.fc(t)  # TxBxH -> TxBxV
        return t.transpose(1, 0)  # TxBxV -> BxTxV

    def get_embeddings(self, tgt):
        """
        :param tgt: BxT
        :return: TxBxE
        """
        t = self.embedding(tgt)  # BxT -> BxTxE
        t = self.positional_encoder(t)
        return t.transpose(1, 0)  # BxTxE -> TxBxE

    def decode(self, memory, tgt, num_memories=None, tgt_length=None):
        """
        :param memory: MxBxE,
        :param tgt: TxBxE,
        :param num_memories: B,
        :param tgt_length: B
        :return: TxBxH
        """
        tgt_mask = generate_attention_mask(tgt.shape[0], device=tgt.device)  # TxT
        tgt_key_padding_mask = generate_padding_mask(tgt_length, self.max_tgt_length, device=tgt.device)  # BxT
        memory_key_padding_mask = generate_padding_mask(num_memories, self.max_memory_length, device=tgt.device)  # BxM
        t = self.decoder(tgt,  # TxBxE
                         memory,  # MxBxE
                         tgt_mask=tgt_mask,  # TxT
                         tgt_key_padding_mask=tgt_key_padding_mask,  # BxT
                         memory_key_padding_mask=memory_key_padding_mask  # BxM
                         )  # TxBxH
        return t  # TxBxH

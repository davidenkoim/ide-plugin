from math import exp
from queue import PriorityQueue

import torch
import torch.nn as nn
from torch.nn import TransformerDecoderLayer, TransformerDecoder
from torch.nn.functional import log_softmax

from model.positionalEncoder import PositionalEncoding
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

    def forward(self, encoder_output, tgt, num_memories=None, tgt_length=None):
        """
        :param encoder_output: MxBxE,
        :param tgt: TxB,
        :param num_memories: B,
        :param tgt_length: B
        :return: TxBxV
        """
        t = self.get_embeddings(tgt)  # TxB -> TxBxE
        t = self.decode(encoder_output, t, num_memories, tgt_length)  # TxBxE -> TxBxH
        t = self.fc(t)  # TxBxH -> TxBxV
        return t  # TxBxV

    def train(self, mode=True):
        if not mode:
            self.max_memory_length = None
            self.max_tgt_length = None
        return super().train(mode)

    def greedy_search(self, encoder_output, length, init_idx, eos_idx):
        """
        :param encoder_output: Mx1xE,
        :param length: length of generated sequence,
        :param init_idx: index of INIT token,
        :param eos_idx: index of EOS token.
        """
        out = torch.full((1, 1), init_idx, device=encoder_output.device, dtype=torch.long)
        for _ in range(length):
            new_logits = self(encoder_output, out, None, None)
            new_token = torch.argmax(new_logits[-1, :, :], dim=-1, keepdim=True)
            if new_token[0, 0] == eos_idx:
                break
            out = torch.cat((out, new_token), dim=0)
        return out[1:, 0]

    # Based on https://github.com/budzianowski/PyTorch-Beam-Search-Decoding/blob/master/decode_beam.py
    def beam_search(self, encoder_output, max_output_length, init_idx, eos_idx, pad_idx=None, topk=10, beam_width=40,
                    batch_size=10):
        """
        :param encoder_output: Mx1xE,
        :param max_output_length: length of generated sequence,
        :param init_idx: index of INIT token,
        :param eos_idx: index of EOS token,
        :param pad_idx: index of PAD token.
        :param topk:
        :param beam_width:
        :param batch_size:
        """

        # Start with the start of the sentence token
        init_token = torch.full((1, 1), init_idx, dtype=torch.long)

        # Number of sentence to generate
        endnodes = []

        # starting node -  previous node, word id, logp, length
        node = BeamSearchNode(init_token, 0)
        nodes = PriorityQueue()

        # start the queue
        nodes.put((-node.eval(), node))
        qsize = 1

        # start beam search
        # give up when decoding takes too long
        while qsize < 10000:
            if batch_size is None:
                # fetch the best node
                score, n = nodes.get()
                qsize -= 1
                decoder_input = n.token_ids

                if decoder_input.shape[0] >= max_output_length or decoder_input[-1, 0] == eos_idx:
                    endnodes.append((score, n))
                    # if we reached maximum # of sentences required
                    if len(endnodes) >= topk:
                        break
                    else:
                        continue

                # decode for one step using decoder
                decoder_output = self(encoder_output, decoder_input.to(encoder_output.device))[-1:, :, :]
                decoder_output = log_softmax(decoder_output, dim=-1).cpu()

                # PUT HERE REAL BEAM SEARCH OF TOP
                log_prob, indexes = torch.topk(decoder_output, beam_width)

                for i in range(beam_width):
                    new_idx = indexes[:, :, i]
                    log_p = log_prob[0, 0, i].item()

                    node = BeamSearchNode(torch.cat((decoder_input, new_idx)), n.log_p + log_p)
                    score = -node.eval()
                    nodes.put((score, node))
                    qsize += 1
            else:
                assert pad_idx is not None, "Specify pad_idx, please."
                # fetch batch of the best nodes
                decoder_inputs, log_ps = [], []
                while qsize > 0 and len(decoder_inputs) < batch_size:
                    score, n = nodes.get()
                    qsize -= 1
                    token_ids = n.token_ids
                    if token_ids.shape[0] >= max_output_length or token_ids[-1, 0] == eos_idx:
                        endnodes.append((score, n))
                        if len(endnodes) >= topk:
                            break
                        else:
                            continue
                    decoder_inputs.append(token_ids)
                    log_ps.append(n.log_p)

                if len(endnodes) >= topk:
                    break

                # pad decoder_inputs
                tgt_lengths = torch.LongTensor(list(map(lambda x: x.shape[0], decoder_inputs)), device=encoder_output.device)
                max_length = tgt_lengths.max()
                decoder_input = torch.cat([
                    torch.constant_pad_nd(inp, (0, 0, 0, max_length - inp.shape[0]), pad_idx)
                    for inp in decoder_inputs], dim=1).to(encoder_output.device)

                # decode batch
                decoder_outputs = self(encoder_output.expand(-1, decoder_input.shape[1], -1), decoder_input, tgt_length=tgt_lengths).cpu()
                for i, (decoder_input, log_p, tgt_length) in enumerate(zip(decoder_inputs, log_ps, tgt_lengths)):
                    decoder_output = log_softmax(decoder_outputs[tgt_length - 1, i], dim=-1)
                    new_log_ps, new_idxs = torch.topk(decoder_output, beam_width)
                    for new_log_p, new_idx in zip(new_log_ps, new_idxs):
                        node = BeamSearchNode(torch.cat((decoder_input, new_idx.view(1, 1))), log_p + new_log_p.item())
                        score = -node.eval()
                        nodes.put((score, node))
                        qsize += 1
        return sorted(endnodes)

    def get_embeddings(self, tgt):
        """
        :param tgt: TxB
        :return: TxBxE
        """
        t = self.embedding(tgt)  # TxB -> TxBxE
        t = self.positional_encoder(t)
        return t  # TxBxE

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


class BeamSearchNode(object):
    def __init__(self, token_ids, log_probability):
        """
        :param token_ids:
        :param log_probability:
        """
        self.token_ids = token_ids
        self.log_p = log_probability

    def eval(self):
        # length = self.token_ids.shape[0]
        # return self.log_p / float(length - 1 + 1e-8)
        return self.log_p

    def __str__(self):
        return f"{self.token_ids_array}: {self.p}"

    def __repr__(self):
        return str(self)

    @property
    def p(self):
        return exp(self.log_p)

    @property
    def token_ids_array(self):
        return list(self.token_ids.reshape(-1).cpu().numpy())

    def __lt__(self, other):
        return self.log_p < other.log_p

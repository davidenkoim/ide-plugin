import json
import time
from abc import ABC, abstractmethod
from collections import Counter
from os.path import join, exists

import torch
from torchtext import data

from utils import sub_tokenizer, camel_case_split, INIT_TOKEN, EOS_TOKEN, UNK_TOKEN, VAR_TOKEN, PAD_TOKEN, to_list, \
    split


class BaseIdDataset(ABC):
    usage_vocab_path = "usage_vocab.json"
    target_vocab_path = "target_vocab.json"
    usage_vocab_specials = [UNK_TOKEN, PAD_TOKEN, VAR_TOKEN]
    target_vocab_specials = [UNK_TOKEN, PAD_TOKEN, INIT_TOKEN, EOS_TOKEN]

    def __init__(self, cfg):
        self.train = None
        self.val = None
        self.test = None

        self.dataset_path = cfg.dataset.path
        self.dataset_type = cfg.dataset.type
        self.lazy_reader_type = getattr(cfg.dataset, "lazy_reader_type", None)
        self.dataset_split_strategy = cfg.dataset.split_strategy
        self.usage_vocab_max_size = cfg.dataset.usage_vocab_max_size
        self.usage_vocab_min_freq = cfg.dataset.usage_vocab_min_freq
        self.target_vocab_max_size = cfg.dataset.target_vocab_max_size
        self.target_vocab_min_freq = cfg.dataset.target_vocab_min_freq

        self.max_sequence_length = cfg.model.max_sequence_length
        self.max_num_usages = cfg.model.max_num_usages
        self.max_target_length = cfg.model.max_target_length
        self.usage_tokenizer_type = cfg.model.usage_tokenizer_type
        self.target_tokenizer_type = cfg.model.target_tokenizer_type

        if self.usage_tokenizer_type == "sub_token":
            usage_tokenizer = sub_tokenizer(length=self.max_sequence_length)
        elif self.usage_tokenizer_type == "split":
            usage_tokenizer = split
        else:
            raise ValueError(f"Usage tokenizer of type {self.usage_tokenizer_type} is not supported")

        if self.target_tokenizer_type is None:
            target_tokenizer = to_list
        elif self.target_tokenizer_type == "camel_case":
            target_tokenizer = camel_case_split
        else:
            raise ValueError(f"Target tokenizer of type {self.usage_tokenizer_type} is not supported")

        # Configuring torchtext fields for automatic padding and numericalization of batches
        self.usages_field = data.NestedField(data.Field(sequential=True,
                                                        use_vocab=True,
                                                        init_token=None,
                                                        eos_token=None,
                                                        fix_length=self.max_sequence_length,
                                                        dtype=torch.long,
                                                        tokenize=usage_tokenizer,
                                                        batch_first=True,
                                                        is_target=False),
                                             fix_length=self.max_num_usages,
                                             include_lengths=True)
        self.target_field = data.Field(sequential=True,
                                       use_vocab=True,
                                       init_token=INIT_TOKEN,
                                       eos_token=EOS_TOKEN,
                                       fix_length=self.max_target_length,
                                       dtype=torch.long,
                                       tokenize=target_tokenizer,
                                       batch_first=True,
                                       is_target=True,
                                       include_lengths=True)
        self.setup()
        self.build_vocabs()

    @abstractmethod
    def setup(self):
        """
        Abstract method to setup train, val, test.
        :return:
        """
        pass

    def build_vocabs(self):
        """
        Method that build vocabs. Must be called after set up.
        :return:
        """
        usage_vocab_path = join(self.dataset_path, self.usage_vocab_path)
        target_vocab_path = join(self.dataset_path, self.target_vocab_path)
        if exists(usage_vocab_path):
            print(f"Loading usage vocabulary from {usage_vocab_path}")
            with open(usage_vocab_path, "r", encoding="utf-8") as f:
                counter = Counter(json.load(f))
            self.usages_field.vocab = self.usages_field.vocab_cls(counter,
                                                                  max_size=self.usage_vocab_max_size,
                                                                  min_freq=self.usage_vocab_min_freq,
                                                                  specials=self.usage_vocab_specials)
            self.usages_field.nesting_field.vocab = self.usages_field.nesting_field.vocab_cls(counter,
                                                                                              max_size=self.usage_vocab_max_size,
                                                                                              min_freq=self.usage_vocab_min_freq,
                                                                                              specials=self.usage_vocab_specials)
        else:
            print("Building usage vocabulary...")
            start_time = time.time()
            self.usages_field.build_vocab(self.train,
                                          max_size=self.usage_vocab_max_size,
                                          min_freq=self.usage_vocab_min_freq,
                                          specials=self.usage_vocab_specials)
            print(f"Built in {time.time() - start_time} s.")
            print(f"Saving usage vocabulary frequencies to {usage_vocab_path}")
            with open(usage_vocab_path, "w+", encoding="utf-8") as f:
                json.dump(self.usage_vocabulary.freqs, f)

        if exists(target_vocab_path):
            print(f"Loading usage vocabulary from {target_vocab_path}")
            with open(target_vocab_path, "r", encoding="utf-8") as f:
                self.target_field.vocab = self.usages_field.vocab_cls(Counter(json.load(f)),
                                                                      max_size=self.target_vocab_max_size,
                                                                      min_freq=self.target_vocab_min_freq,
                                                                      specials=self.target_vocab_specials)
        else:
            print("Building target vocabulary...")
            start_time = time.time()
            self.target_field.build_vocab(self.train,
                                          max_size=self.target_vocab_max_size,
                                          min_freq=self.target_vocab_min_freq,
                                          specials=self.target_vocab_specials)
            print(f"Built in {time.time() - start_time} s.")
            print(f"Saving target vocabulary frequencies to {target_vocab_path}")
            with open(target_vocab_path, "w+", encoding="utf-8") as f:
                json.dump(self.target_vocabulary.freqs, f)
        print(f"\nUsage vocabulary size is {len(self.usage_vocabulary)}")
        print(f"Target vocabulary size is {len(self.target_vocabulary)}")

    @property
    def usage_vocabulary(self):
        return self.usages_field.vocab

    @property
    def target_vocabulary(self):
        return self.target_field.vocab

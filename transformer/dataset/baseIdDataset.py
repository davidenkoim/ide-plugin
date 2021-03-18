import json
import time
from abc import ABC, abstractmethod
from collections import Counter
from itertools import chain
from os.path import join, exists

import torch
from torchtext.legacy import data
from torchtext.vocab import Vocab
from tqdm.autonotebook import tqdm

from utils import sub_tokenizer, camel_case_split, INIT_TOKEN, EOS_TOKEN, UNK_TOKEN, VAR_TOKEN, PAD_TOKEN, to_list, \
    split, STR_TOKEN, NUM_TOKEN


class BaseIdDataset(ABC):
    usage_vocab_path = "usage_vocab.json"
    target_vocab_path = "target_vocab.json"
    usage_vocab_specials = [UNK_TOKEN, PAD_TOKEN, VAR_TOKEN, STR_TOKEN, NUM_TOKEN]
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
        self.usage_field = data.NestedField(data.Field(sequential=True,
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
                                       batch_first=False,
                                       is_target=True,
                                       include_lengths=True)
        self.example_fields = {'variable': ('target', self.target_field),
                               'ngrams': ('usages', self.usage_field)}
        self.dataset_fields = {'target': self.target_field,
                               'usages': self.usage_field}
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
        if exists(usage_vocab_path) and exists(target_vocab_path):
            print(f"Loading usage counter from {usage_vocab_path}")
            with open(usage_vocab_path, "r", encoding="utf-8") as f:
                usage_counter = Counter(json.load(f))
            print(f"Loading target counter from {target_vocab_path}")
            with open(target_vocab_path, "r", encoding="utf-8") as f:
                target_counter = Counter(json.load(f))
        else:
            print("Building vocabulary counters...")
            start_time = time.time()
            usage_counter, target_counter = self.build_counters([self.usage_field, self.target_field],
                                                                self.train)
            print(f"Built in {time.time() - start_time} s.")
            print(f"Saving usage vocabulary frequencies to {usage_vocab_path}")
            with open(usage_vocab_path, "w+", encoding="utf-8") as f:
                json.dump(usage_counter, f)
            print(f"Saving target vocabulary frequencies to {target_vocab_path}")
            with open(target_vocab_path, "w+", encoding="utf-8") as f:
                json.dump(target_counter, f)

        self.usage_field.vocab = Vocab(usage_counter,
                                       max_size=self.usage_vocab_max_size,
                                       min_freq=self.usage_vocab_min_freq,
                                       specials=self.usage_vocab_specials)
        self.usage_field.nesting_field.vocab = Vocab(usage_counter,
                                                     max_size=self.usage_vocab_max_size,
                                                     min_freq=self.usage_vocab_min_freq,
                                                     specials=self.usage_vocab_specials)

        self.target_field.vocab = Vocab(target_counter,
                                        max_size=self.target_vocab_max_size,
                                        min_freq=self.target_vocab_min_freq,
                                        specials=self.target_vocab_specials)

        print(f"\nUsage vocabulary size is {len(self.usage_vocabulary)}")
        print(f"Target vocabulary size is {len(self.target_vocabulary)}")

    @property
    def usage_vocabulary(self):
        return self.usage_field.vocab

    @property
    def target_vocabulary(self):
        return self.target_field.vocab

    @staticmethod
    def build_counters(fields, dataset):
        nc_list = [(name, Counter()) for field1 in fields for name, field2 in dataset.fields.items() if
                   field2 is field1]
        for ex in tqdm(dataset, mininterval=0.5):
            for field_name, counter in nc_list:
                ex_field = getattr(ex, field_name)
                if len(ex_field) > 0:
                    counter.update(chain.from_iterable(ex_field) if isinstance(ex_field[0], list) else ex_field)
        return list(zip(*nc_list))[1]

    def is_training(self, b):
        if b:
            self.usage_field.include_lengths = True
            self.usage_field.fix_length = self.max_num_usages
        else:
            self.usage_field.include_lengths = False
            self.usage_field.fix_length = None

    def input_from_usages(self, usages, limit_num_usages=20, device="cpu"):
        src = usages[:limit_num_usages]
        src = self.usage_field.preprocess(src)
        return self.usage_field.process([src], device=device)

    def ids_to_tokens(self, token_ids):
        if isinstance(token_ids, list):
            return list(map(self.ids_to_tokens, token_ids))
        else:
            return self.target_vocabulary.itos[token_ids]

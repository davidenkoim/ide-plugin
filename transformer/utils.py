import json
import re

import torch

INIT_TOKEN = "<s>"
EOS_TOKEN = "</s>"
PAD_TOKEN = "<pad>"
UNK_TOKEN = "<unk>"
VAR_TOKEN = "<var>"
STR_TOKEN = "<str>"
NUM_TOKEN = "<num>"
TOKEN_DELIMITER = "\u2581"

JAVA_SMALL_TRAIN = {"cassandra-dtest-shaded", "gradle", "intellij-community", "presto-root", "wildfly-parent",
                    "elasticsearch", "hibernate-orm", "liferay-portal", "spring"}
JAVA_SMALL_VAL = {"gdx-parent"}
JAVA_SMALL_TEST = {"hadoop"}


def generate_padding_mask(num_usages, max_usages=None, device=None):  # B
    """
    Generate mask that indicates padding elements, for example:

    [
        [0,0,1],\n
        [0,0,0],\n
        [0,1,1],\n
        [0,0,1]\n
    ]

    :param num_usages: B
    :param max_usages: Integer
    :param device: the device on which to store mask
    :return: Bx(max_usages)
    """
    if num_usages is None:
        return
    b = len(num_usages)
    max_usages = max(num_usages) if max_usages is None else max_usages
    mask = torch.ones(b, max_usages, dtype=torch.bool)
    for i, j in enumerate(num_usages):
        mask[i, :j] = False
    return mask.to(device)


def generate_attention_mask(size, device=None):
    """
    Generate mask (size)x(size) of type:

    [
        [  0., -inf, -inf], \n
        [  0.,   0., -inf], \n
        [  0.,   0.,   0.]
    ]
    """
    mask = torch.tril(torch.ones(size, size)).log()
    return mask.to(device)


def top_predictions(predictions, n=10):
    """
    Calculate top n predictions.

    :param predictions: BxV
    :param n: top n
    :return: Bxn -- top n predictions
    """
    with torch.no_grad():
        return torch.argsort(predictions, dim=1, descending=True)[:, :n]


def read_json(file):
    with open(file, 'r', encoding='utf-8') as f:
        return json.loads(f.read())


def to_list(name):
    return [name]


def split(name):
    return name.split(TOKEN_DELIMITER)


def camel_case_split(name):
    r"""
    Splits CamelCase name to tokens and removes underscore character.

    Example:

    >>> camel_case_split('__CamelCase_word__') == ['__', 'camel', 'case', 'word', '__']

    :param name: name that you want to tokenize,
    :return: list of tokens.
    """
    if name in [VAR_TOKEN, STR_TOKEN, NUM_TOKEN]:
        return [name]
    return re.sub('([A-Z]?[a-z]+)', r' \1 ',
                  re.sub('([A-Z]+)', r' \1',
                         re.sub(r'(?<=[A-Za-z0-9])(_+)(?=[A-Za-z0-9])', r' ', name)
                         )
                  ).lower().split()


def sub_token_split(name, length=None):
    # length must be odd
    tokens = name.split(TOKEN_DELIMITER)
    subtokens = []
    if length is None:
        length = len(tokens)
    center = len(tokens) // 2
    l = length // 2
    left_side, right_side = [], []
    for i in range(center - 1, 0, -1):
        left_side.extend(reversed(camel_case_split(tokens[i])))
        if len(left_side) >= l:
            break
    left_side = list(reversed(left_side[:l]))
    for i in range(center + 1, len(tokens)):
        right_side.extend(camel_case_split(tokens[i]))
        if len(right_side) >= l:
            break
    right_side = right_side[:l]
    return left_side + [tokens[center]] + right_side


def sub_tokenizer(length=11):
    return lambda x: sub_token_split(x, length)


def camel_case_tokenizer(b=True):
    """
    :param b: True -- return function that tokenize name as CamelCase string, False -- string -> [string],
    :return: tokenization function.
    """
    if b:
        return camel_case_split
    else:
        return to_list


def reverseSplit(string):
    return list(reversed(string.split()))

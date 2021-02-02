import json
import re

import torch


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


def camelCaseSplit(name):
    r"""
    Splits CamelCase name to tokens and removes underscore character.

    Example:

    >>> camelCaseSplit('CamelCase_word') == ['camel', 'case', 'word']

    :param name: name that you want to tokenize,
    :return: list of tokens.
    """
    return re.sub('([A-Z][a-z]+)', r' \1',
                  re.sub('([A-Z]+)', r' \1',
                         re.sub('(_+)', r' ', name)
                         )
                  ).lower().split()


def camelCaseTokenizer(b=True):
    """
    :param b: True -- return function that tokenize name as CamelCase string, False -- string -> [string],
    :return: tokenization function.
    """
    if b:
        return camelCaseSplit
    else:
        def toList(name):
            return [name]

        return toList


def reverseSplit(string):
    return list(reversed(string.split()))

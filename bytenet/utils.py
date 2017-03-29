import json
import codecs
import itertools
import numpy as np

# If length -1, no validation data
def loadDataSet(file, length = -1):
    with codecs.open(file, encoding = 'utf-8') as f:
        arr = json.load(f)
        if length == -1: return arr, []
        else:
            assert(length * 2 <= len(arr))
            return arr[0:length], arr[-length:]

def text_from_string(text):
    return [ord(c) for c in text]

def _load_generator_data(text, sample_size):
    mod_size = len(text) - len(text) % sample_size
    return text[0:mod_size]

def load_generator_data(texts, sample_size):
    return np.array(
        # Shorten each text to a multiple of sample_size
        list(itertools.chain.from_iterable(
            [_load_generator_data(text, sample_size) for text in texts]
        )),
        dtype='int32'
    ).reshape(-1, sample_size)

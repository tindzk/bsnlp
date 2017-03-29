# NER
## ByteNet
ByteNet was introduced in DeepMind's paper [Neural Machine Translation in Linear Time](https://arxiv.org/abs/1610.10099).

> The ByteNet decoder attains state-of-the-art performance on character-level language modeling and outperforms the previous best results obtained with recurrent neural networks.  The ByteNet also achieves a performance on raw character-level machine translation that approaches that of the best neural translation models that run in quadratic time. The implicit structure learnt by the ByteNet mirrors the expected alignments between the sequences.

The architecture has been adapted to NER. The code was largely borrowed from Paarth Neekhara's [TensorFlow implementation](https://github.com/paarthneekhara/byteNet-tensorflow).

## License
This project is licensed under the terms of the MIT License.

## Authors
* Tim Nieradzik

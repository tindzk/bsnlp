import json
import codecs
import numpy as np
from keras import backend as K
from keras.models import Model
from keras.layers import Dense, Activation, LSTM, TimeDistributed, \
    Convolution1D, MaxPooling1D, Highway, merge, Input, Masking, Bidirectional, \
    Flatten, GlobalMaxPooling1D, AtrousConvolution1D
from keras.callbacks import Callback, ModelCheckpoint, ReduceLROnPlateau, CSVLogger
from keras.layers.normalization import BatchNormalization
from keras.layers.recurrent import GRU

Epochs         = 20
BatchSize      = 128
SequenceLength = 100

# If length -1, no validation data
def loadDataSet(file, length = -1):
    with codecs.open(file, encoding = 'utf-8') as f:
        arr = json.load(f)
        if length == -1: return arr, []
        else:
            assert(length * 2 <= len(arr))
            return arr[0:length], arr[-length:]

def chars(text):
    return sorted(list(set([t[0] for t in text])))

def processData(data, charsToIndices, numChars):
    sequences = int(len(data) / SequenceLength)

    X = np.zeros((sequences, SequenceLength, numChars), dtype = np.bool)
    y = np.zeros((sequences, SequenceLength, 1), dtype = np.bool)

    for sequence in range(0, sequences):
        start = sequence * SequenceLength
        for ofs, s in enumerate(data[start: start + SequenceLength]):
            char   = s[0]
            entity = s[1]

            if char in charsToIndices:
                X[sequence, ofs, charsToIndices[char]] = 1
            y[sequence, ofs] = entity

    return X, y

def processDataFit(data, charsToIndices, numChars):
    X = np.zeros((1, SequenceLength, numChars), dtype = np.bool)
    # TODO Consider entire input
    for ofs, char in enumerate(data[0: SequenceLength]):
        if char in charsToIndices:
            X[0, ofs, charsToIndices[char]] = 1
    return X

def batches(trainingData, validationData, chars):
    charsToIndices = dict((c, i) for i, c in enumerate(chars))

    trainingX,   trainingY   = processData(trainingData,   charsToIndices, len(chars))
    validationX, validationY = processData(validationData, charsToIndices, len(chars))

    return trainingX, trainingY, \
            validationX, validationY

##
# GRU
##

def archGru(input):
    x = BatchNormalization()(input)
    x = GRU(256, return_sequences = True)(x)

    x = Dense(1)(x)
    x = Activation('softmax')(x)

    return x, "gru"

def train(arch, batches, numChars):
    input        = Input(shape = (SequenceLength, numChars))
    output, name = arch(input)

    model = Model(input = input, output = output)
    model.compile(optimizer = 'nadam',
                  loss      = 'binary_crossentropy',
                  metrics   = ['precision', 'recall'])
    print(model.summary())

    trainingX, trainingY, validationX, validationY = batches

    cp = ModelCheckpoint(
        filepath       = "results/model-" + name + ".hdf5",
        monitor        = "loss",  # TODO val_loss
        verbose        = 1,
        save_best_only = True,
        mode           = "min"
    )

    lr  = ReduceLROnPlateau(monitor = "loss")
    csv = CSVLogger("results/model-" + name + ".csv")

    model.fit(trainingX,
              trainingY,
              nb_epoch        = Epochs,
              batch_size      = BatchSize,
              validation_data = (validationX, validationY),
              callbacks       = [cp, lr, csv]
             )

def predict(arch, chars, text):
    charsToIndices = dict((c, i) for i, c in enumerate(chars))

    input        = Input(shape = (SequenceLength, len(chars)))
    output, name = arch(input)

    model = Model(input = input, output = output)
    model.load_weights("results/model-" + name + ".hdf5")

    X = processDataFit(text, charsToIndices, len(chars))
    prediction = model.predict(X)

    print(text[0: SequenceLength])
    for i in range(SequenceLength):
        char = '-' if prediction[0][i] == 1 else ' '
        print(char, end = "", flush = True)

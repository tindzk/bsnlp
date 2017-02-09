import json
import model

N = -1
trainingData, validationData = model.loadDataSet("data-pl.json", N)

chars   = model.chars(trainingData)
batches = model.batches(trainingData, validationData, chars)

with open('results/chars.json', 'w') as file:
    json.dump(chars, file, ensure_ascii = False)

model.train(model.archGru, batches, len(chars))

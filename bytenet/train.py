import os
import argparse
import tensorflow as tf

import utils
import model

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--learning_rate', type=float, default=0.001,
                       help='Learning Rate')
    parser.add_argument('--batch_size', type=int, default=1,
                       help='Learning Rate')
    parser.add_argument('--max_epochs', type=int, default=1000,
                       help='Max Epochs')
    parser.add_argument('--beta1', type=float, default=0.5,
                       help='Momentum for Adam Update')
    parser.add_argument('--resume_model', type=str, default=None,
                       help='Pre-Trained Model Path, to resume from')
    parser.add_argument('--data_dir', type=str, default='Data',
                       help='Data Directory')
    parser.add_argument('--log_dir', type=str, default='logs',
                        help='Path to TensorBoard logs')

    args = parser.parse_args()

    '''
    options
    n_source_quant : quantization channels of source text
    n_target_quant : quantization channels of target text
    residual_channels : number of channels in internal blocks
    batch_size : Batch Size
    sample_size : Text Sample Length
    decoder_filter_width : Decoder Filter Width
    decoder_dilations : Dilation Factor for decoder layers (list)
    '''
    config = {
        "decoder_filter_width": 3,
        "sample_size": 500,
        "decoder_dilations": [1, 2, 4, 8, 16,
                              1, 2, 4, 8, 16,
                              1, 2, 4, 8, 16,
                              1, 2, 4, 8, 16,
                              1, 2, 4, 8, 16
                              ],
        "residual_channels": 512,
        "n_target_quant": 256,
        "n_source_quant": 256,
        'batch_size' : args.batch_size
    }

    articles = -1  # TODO
    trainingData, validationData = utils.loadDataSet("../data-pl.json", articles)

    tensors = model.build_prediction_model(config)

    # Set up logging for TensorBoard
    writer = tf.summary.FileWriter(args.log_dir, graph=tf.get_default_graph())
    run_metadata = tf.RunMetadata()
    summaries = tf.summary.merge_all()

    optim = tf.train.AdamOptimizer(args.learning_rate, beta1 = args.beta1) \
        .minimize(tensors['loss'], var_list=tensors['variables'])

    sess = tf.InteractiveSession()
    tf.global_variables_initializer().run()
    saver = tf.train.Saver()

    if args.resume_model:
        saver.restore(sess, args.resume_model)

    # TODO Segment sentences
    text_samples = utils.load_generator_data(
        [utils.text_from_string(item[0]) for item in trainingData],
        config['sample_size']
    )
    labels_samples = utils.load_generator_data(
        [item[1] for item in trainingData],
        config['sample_size']
    )

    models_path = "/dev/shm/bytenet-ner/"
    if not os.path.exists(models_path): os.makedirs(models_path)

    # Print 100 first chars of the first sequence
    batch = 0
    chars = 100

    for epoch in range(args.max_epochs):
        step = 0
        batch_size = args.batch_size
        while (step + 1) * batch_size < text_samples.shape[0]:
            text_batch   = text_samples  [step*batch_size : (step + 1)*batch_size, :]
            labels_batch = labels_samples[step*batch_size : (step + 1)*batch_size, :]

            _, summary, loss, prediction = sess.run([optim, summaries, tensors['loss'], tensors['prediction']], feed_dict = {
                tensors['sentence'] : text_batch,
                tensors['labels'] : labels_batch
            })

            print("Epoch", epoch, "  Step", step, "  Loss", loss)

            for i in range(chars):
                c = chr(text_batch[batch][i])
                if c == '\n': c = ' '
                print(c, end = "")
            print("")
            for i in range(chars):
                char = ' ' if prediction[batch][i] == 0 else '-'
                print(char, end = "")
            print("")
            print("********************************************************")
            print("")

            writer.add_summary(summary, step)
            writer.add_run_metadata(run_metadata, 'step_{:04d}'.format(step))

            step += 1

            if step % 500 == 0:
                saver.save(sess, models_path + "model_epoch_{}.ckpt".format(epoch))

if __name__ == '__main__':
    main()

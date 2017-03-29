import tensorflow as tf

import ops

def w_source_embedding(options):
    return tf.get_variable('w_source_embedding',
        [options['n_source_quant'], 2 * options['residual_channels']],
        initializer=tf.truncated_normal_initializer(stddev=0.02))

def loss(options, decoder_output, target_sentence):
    target_one_hot = tf.one_hot(target_sentence, depth = options['n_target_quant'], dtype = tf.float32)
    ce = tf.nn.softmax_cross_entropy_with_logits(decoder_output, target_one_hot, name='decoder_cross_entropy_loss')
    return tf.reduce_sum(ce, name = "Reduced_mean_loss")

# Residual block
def decode_layer(options, input_, dilation, layer_no):
    # Input dimension
    in_dim = input_.get_shape().as_list()[-1]

    # Reduce dimension
    relu1 = tf.nn.relu(input_, name = 'dec_relu1_layer{}'.format(layer_no))
    conv1 = ops.conv1d(relu1, in_dim / 2, name = 'dec_conv1d_1_layer{}'.format(layer_no))

    # 1 x k dilated convolution
    relu2 = tf.nn.relu(conv1, name = 'enc_relu2_layer{}'.format(layer_no))
    dilated_conv = ops.dilated_conv1d(
        relu2,
        output_channels = in_dim / 2,
        dilation        = dilation,
        filter_width    = options['decoder_filter_width'],
        causal          = True,
        name            = "dec_dilated_conv_layer{}".format(layer_no))

    # Restore dimension
    relu3 = tf.nn.relu(dilated_conv, name = 'dec_relu3_layer{}'.format(layer_no))
    conv2 = ops.conv1d(relu3, in_dim, name = 'dec_conv1d_2_layer{}'.format(layer_no))

    # Residual connection
    return input_ + conv2

def decoder(options, input_):
    for layer_no, dilation in enumerate(options['decoder_dilations']):
        input_ = decode_layer(options, input_, dilation, layer_no)

    return ops.conv1d(
        input_,
        options['n_target_quant'],
        name = 'decoder_post_processing')

def build_prediction_model(options):
    sentence = tf.placeholder('int32', [options['batch_size'], options['sample_size']], name = 'sentence')
    labels   = tf.placeholder('int32', [options['batch_size'], options['sample_size']], name = 'labels')

    source_embedding = tf.nn.embedding_lookup(w_source_embedding(options), sentence, name = "source_embedding")
    decoder_output = decoder(options, source_embedding)
    prediction = tf.argmax(decoder_output, 2)

    l = loss(options, decoder_output, labels)
    tf.summary.scalar('loss', l)

    variables = tf.trainable_variables()

    return {
        'sentence' : sentence,
        'labels' : labels,
        'loss' : l,
        'prediction' : prediction,
        'variables' : variables
    }

def build_classifier(options, sample_size, reuse = False):
    if reuse:
        tf.get_variable_scope().reuse_variables()

    source_sentence = tf.placeholder('int32', [1, sample_size], name = 'sentence')
    source_embedding = tf.nn.embedding_lookup(w_source_embedding(), source_sentence, name = "source_embedding")
    decoder_output = decoder(options, source_embedding)
    prediction = tf.argmax(decoder_output, 2)
    probs = tf.nn.softmax(decoder_output)

    return {
        'source_sentence' : source_sentence,
        'prediction' : prediction,
        'probs' : probs
    }

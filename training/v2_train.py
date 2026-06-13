import os, json, re, time, gc
from collections import Counter

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
!pip install -q tqdm 2>/dev/null

import tensorflow as tf
from tensorflow.keras import layers, Model, backend as K
from tqdm.notebook import tqdm
import numpy as np

print(f"GPU: {tf.config.list_physical_devices('GPU')}")
print(f"TF: {tf.__version__}")

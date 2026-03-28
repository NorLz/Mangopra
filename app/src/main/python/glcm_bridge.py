import math

import cv2
import numpy as np

try:
    from skimage.feature import graycomatrix, graycoprops
except ImportError:
    try:
        from skimage.feature import greycomatrix as graycomatrix
        from skimage.feature import greycoprops as graycoprops
    except ImportError:
        from skimage.feature.texture import greycomatrix as graycomatrix
        from skimage.feature.texture import greycoprops as graycoprops


FEATURE_NAMES = ("contrast", "dissimilarity", "correlation", "energy", "homogeneity")
ANGLES = (0.0, math.pi / 4.0, math.pi / 2.0, 3.0 * math.pi / 4.0)


def _rgb_to_array(rgb_bytes, width, height):
    if width <= 0 or height <= 0:
        raise ValueError(f"Invalid image size: width={width}, height={height}")

    expected = width * height * 3
    if isinstance(rgb_bytes, (bytes, bytearray, memoryview)):
        array = np.frombuffer(rgb_bytes, dtype=np.uint8)
    else:
        array = np.asarray(list(rgb_bytes), dtype=np.uint8)

    if array.size != expected:
        raise ValueError(f"Expected {expected} RGB values, got {array.size}")

    return array.reshape((height, width, 3))


def extract_glcm_features_from_rgb(rgb_bytes, width, height):
    rgb = _rgb_to_array(rgb_bytes, int(width), int(height))
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    gray = (gray // 8).astype(np.uint8)
    gray = np.clip(gray, 0, 31)

    glcm = graycomatrix(
        gray,
        distances=[1],
        angles=ANGLES,
        levels=32,
        symmetric=True,
        normed=True,
    )

    return [float(graycoprops(glcm, feature).mean()) for feature in FEATURE_NAMES]

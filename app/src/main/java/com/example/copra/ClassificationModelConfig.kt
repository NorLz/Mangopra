package com.example.copra

import android.content.Context

enum class ClassificationInputType {
    IMAGE_ONLY,
    IMAGE_AND_GLCM
}

data class ClassificationModelOption(
    val key: String,
    val assetFileName: String,
    val displayName: String,
    val inputType: ClassificationInputType
)

object ClassificationModels {
    val MOBILE_NET_V2_BASELINE = ClassificationModelOption(
        key = "mobilenetv2_baseline",
        assetFileName = "mobilenetv2_baseline_phase2_float32_metadata.tflite",
        displayName = "MobileNetV2 Baseline",
        inputType = ClassificationInputType.IMAGE_ONLY
    )

    val MOBILE_NET_V2_GLCM = ClassificationModelOption(
        key = "mobilenetv2_glcm",
        assetFileName = "mobilenetv2_glcm_phase2_float32_metadata.tflite",
        displayName = "MobileNetV2 + GLCM",
        inputType = ClassificationInputType.IMAGE_AND_GLCM
    )

    val SHUFFLE_NET_V2_BASELINE = ClassificationModelOption(
        key = "shufflenetv2_baseline",
        assetFileName = "shufflenetv2_baseline_phase2_float32_metadata.tflite",
        displayName = "ShuffleNetV2 Baseline",
        inputType = ClassificationInputType.IMAGE_ONLY
    )

    val SHUFFLE_NET_V2_GLCM = ClassificationModelOption(
        key = "shufflenetv2_glcm",
        assetFileName = "shufflenetv2_glcm_phase2_float32_metadata.tflite",
        displayName = "ShuffleNetV2 + GLCM",
        inputType = ClassificationInputType.IMAGE_AND_GLCM
    )

    val SQUEEZE_NET_BASELINE = ClassificationModelOption(
        key = "squeezenet_baseline",
        assetFileName = "squeezenet_baseline_phase2_float32_metadata.tflite",
        displayName = "SqueezeNet Baseline",
        inputType = ClassificationInputType.IMAGE_ONLY
    )

    val SQUEEZE_NET_GLCM = ClassificationModelOption(
        key = "squeezenet_glcm",
        assetFileName = "squeezenet_glcm_phase2_float32_metadata.tflite",
        displayName = "SqueezeNet + GLCM",
        inputType = ClassificationInputType.IMAGE_AND_GLCM
    )

    val all = listOf(
        MOBILE_NET_V2_BASELINE,
        MOBILE_NET_V2_GLCM,
        SHUFFLE_NET_V2_BASELINE,
        SHUFFLE_NET_V2_GLCM,
        SQUEEZE_NET_BASELINE,
        SQUEEZE_NET_GLCM
    )

    val default = SQUEEZE_NET_GLCM

    fun fromKey(key: String?): ClassificationModelOption {
        return all.firstOrNull { it.key == key } ?: default
    }
}

class ClassificationModelStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "classification_model_prefs"
        private const val KEY_DEFAULT_MODEL = "default_model"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedModel(): ClassificationModelOption {
        return ClassificationModels.fromKey(prefs.getString(KEY_DEFAULT_MODEL, ClassificationModels.default.key))
    }

    fun setSelectedModel(model: ClassificationModelOption) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, model.key).apply()
    }
}

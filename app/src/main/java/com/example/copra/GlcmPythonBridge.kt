package com.example.copra

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class GlcmPythonBridge(
    private val context: Context
) {
    @Volatile
    private var module: PyObject? = null

    @Synchronized
    fun extractFeaturesFromRgb(rgbBytes: ByteArray, width: Int, height: Int): FloatArray {
        val pythonModule = getModule()
        val result = pythonModule.callAttr("extract_glcm_features_from_rgb", rgbBytes, width, height)
        val values = result.asList().map { it.toFloat() }

        require(values.size == 5) {
            "Expected 5 GLCM features, got ${values.size}"
        }

        return values.toFloatArray()
    }

    private fun getModule(): PyObject {
        module?.let { return it }

        synchronized(this) {
            module?.let { return it }

            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }

            return Python.getInstance()
                .getModule("glcm_bridge")
                .also { module = it }
        }
    }
}

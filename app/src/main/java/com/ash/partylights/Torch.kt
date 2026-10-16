package com.ash.partylights

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread

/** The camera LED used as a white strobe. Calls run on a background thread. */
class Torch(context: Context) {
    private val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? = try {
        cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }
    private val thread = HandlerThread("torch").apply { start() }
    private val handler = Handler(thread.looper)
    private var isOn = false

    val available: Boolean get() = cameraId != null

    fun flash(ms: Long = 50) {
        if (cameraId == null) return
        handler.post { set(true) }
        handler.postDelayed({ set(false) }, ms)
    }

    fun off() {
        handler.removeCallbacksAndMessages(null)
        handler.post { set(false) }
    }

    private fun set(on: Boolean) {
        if (on == isOn) return
        try {
            cm.setTorchMode(cameraId ?: return, on)
            isOn = on
        } catch (_: Exception) {
        }
    }

    fun release() {
        off()
        thread.quitSafely()
    }
}

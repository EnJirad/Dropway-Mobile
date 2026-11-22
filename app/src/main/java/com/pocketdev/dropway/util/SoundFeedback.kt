package com.pocketdev.dropway.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Ported from Dropway Web src/lib/beep.ts
 * Provides instant audio and haptic feedback during scanning and confirmation.
 */
object SoundFeedback {

    private var toneGen: ToneGenerator? = null

    private fun getToneGenerator(): ToneGenerator? {
        if (toneGen == null) {
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            } catch (_: Exception) {}
        }
        return toneGen
    }

    /**
     * บี๊บตอนจับภาพ (Capture): ผู้ใช้รู้ว่ากล้องจับภาพติดแล้วแม้ไม่มองจอ
     */
    fun playCaptureSound(context: Context) {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 75)
            vibrate(context, 40)
        } catch (_: Exception) {}
    }

    /**
     * เสียงสำเร็จ (Success): เมื่อพบบ้าน หรือเมื่อเพิ่มเข้าคิวสำเร็จ
     */
    fun playSuccessSound(context: Context) {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            vibrate(context, 60)
        } catch (_: Exception) {}
    }

    /**
     * เสียงเตือน (Warning): เมื่อพัสดุซ้ำ หรือตรวจจับข้อผิดพลาด
     */
    fun playWarningSound(context: Context) {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            vibrate(context, 100)
        } catch (_: Exception) {}
    }

    private fun vibrate(context: Context, durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }
}

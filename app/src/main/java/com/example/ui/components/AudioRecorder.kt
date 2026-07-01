package com.example.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun startRecording(file: File): Boolean {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            this.recorder = recorder
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "startRecording failed", e)
            recorder.release()
            return false
        }
    }

    fun stopRecording() {
        recorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "stopRecording failed (likely stopped too quickly)", e)
            } finally {
                release()
            }
        }
        recorder = null
    }
}

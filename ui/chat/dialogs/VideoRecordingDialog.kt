package com.fitnesslemon.app.ui.chat.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.fitnesslemon.app.R
import com.fitnesslemon.app.ui.chat.utils.VideoRecorderHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecordingDialog : DialogFragment() {

    private var videoRecorderHelper: VideoRecorderHelper? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var onVideoRecorded: ((File, Int) -> Unit)? = null

    companion object {
        private const val TAG = "VideoRecordingDialog"

        fun newInstance(onVideoRecorded: (File, Int) -> Unit): VideoRecordingDialog {
            val fragment = VideoRecordingDialog()
            fragment.onVideoRecorded = onVideoRecorded
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_video_recording, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val surfaceView = view.findViewById<android.view.SurfaceView>(R.id.surfaceView)
        val btnRecord = view.findViewById<ImageButton>(R.id.btnRecord)
        val btnSwitchCamera = view.findViewById<ImageButton>(R.id.btnSwitchCamera)
        val btnCancel = view.findViewById<ImageButton>(R.id.btnCancel)
        val tvTimer = view.findViewById<TextView>(R.id.tvTimer)
        val tvHint = view.findViewById<TextView>(R.id.tvHint)

        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        videoRecorderHelper = VideoRecorderHelper(
            context = requireContext(),
            surfaceView = surfaceView,
            onRecordingComplete = { file, duration ->
                dismiss()
                onVideoRecorded?.invoke(file, duration)
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )

        btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        btnSwitchCamera.setOnClickListener {
            videoRecorderHelper?.switchCamera()
        }

        btnCancel.setOnClickListener {
            videoRecorderHelper?.cancelRecording()
            dismiss()
        }

        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "video_${timeStamp}.mp4"
        recordingFile = File(requireContext().cacheDir, videoFileName)

        val success = videoRecorderHelper?.startRecordingWithFile(recordingFile!!, true) ?: false
        if (success) {
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            handler.post(timerRunnable!!)
            view?.findViewById<ImageButton>(R.id.btnRecord)?.setImageResource(R.drawable.ic_stop)
            view?.findViewById<TextView>(R.id.tvHint)?.text = "Отпустите для отправки"
        } else {
            Toast.makeText(requireContext(), "Не удалось начать запись", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        handler.removeCallbacks(timerRunnable!!)
        val result = videoRecorderHelper?.stopRecording()
        isRecording = false
        view?.findViewById<ImageButton>(R.id.btnRecord)?.setImageResource(R.drawable.ic_video)
        view?.findViewById<TextView>(R.id.tvHint)?.text = "Удерживайте для записи"

        if (result == null) {
            Toast.makeText(requireContext(), "Ошибка записи видео", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ),
            100
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        videoRecorderHelper?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
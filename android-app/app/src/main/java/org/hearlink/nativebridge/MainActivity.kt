package org.hearlink.nativebridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Native proof-of-concept. Requests the phone's built-in mic for capture and a
 * Bluetooth A2DP media device for playback. Android treats routing as a preference.
 */
class MainActivity : Activity() {
    private lateinit var audioManager: AudioManager
    private lateinit var status: TextView
    private lateinit var level: TextView
    private lateinit var gainBar: SeekBar
    @Volatile private var gain = 0.5f
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    @Volatile private var chosenOutput: AudioDeviceInfo? = null
    private val sampleRate = 48000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        buildUi()
        refreshBluetoothOutput()
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 42)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            gravity = Gravity.TOP
        }
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, 8, 0, 8)
        }
        root.addView(text("HearLink Native Audio Bridge", 24f))
        root.addView(text("Phone microphone input → amplified Bluetooth media output"))
        status = text("Stopped.")
        root.addView(status)
        level = text("Phone-mic input peak: —")
        root.addView(level)
        root.addView(text("Output gain — start low", 16f))
        gainBar = SeekBar(this).apply {
            max = 200
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    gain = progress / 100.0f
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(gainBar)
        root.addView(Button(this).apply {
            text = "Refresh Bluetooth outputs"
            setOnClickListener { refreshBluetoothOutput() }
        })
        root.addView(Button(this).apply {
            text = "Start phone mic → Bluetooth"
            setOnClickListener { startBridge() }
        })
        root.addView(Button(this).apply {
            text = "Stop audio"
            setOnClickListener { stopBridge() }
        })
        root.addView(Button(this).apply {
            text = "Open Android sound settings"
            setOnClickListener {
                try { startActivity(android.content.Intent("android.settings.SOUND_SETTINGS")) }
                catch (_: Exception) { updateStatus("Open Bluetooth settings manually; enable Media audio for the neckband.") }
            }
        })
        root.addView(text(
            "Setup: connect the boAt neckband, keep Media audio ON, and initially turn Call audio OFF. " +
                "Refresh outputs, select the neckband if listed, then start.",
            14f
        ))
        root.addView(text(
            "Safety: begin at low gain. Stop immediately for feedback, discomfort, or painful loudness. " +
                "This is a prototype, not a hearing aid.",
            14f
        ))
        setContentView(root)
    }

    private fun refreshBluetoothOutput() {
        try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            if (outputs.isEmpty()) {
                chosenOutput = null
                updateStatus("No Bluetooth A2DP media output exposed. Connect the neckband and enable Media audio.")
                return
            }
            chosenOutput = outputs.firstOrNull {
                it.productName.toString().contains("boAt", ignoreCase = true)
            } ?: outputs.first()
            updateStatus("Selected Bluetooth output: ${chosenOutput?.productName}. Call audio should be OFF for this test.")
        } catch (_: SecurityException) {
            updateStatus("Bluetooth permission needed. Allow Nearby devices, then refresh outputs.")
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    private fun startBridge() {
        if (running.get()) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            updateStatus("Microphone permission is required. Grant it, then tap Start again.")
            return
        }
        try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val builtInMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            if (builtInMic == null) {
                updateStatus("Android did not expose a built-in phone microphone. Audio not started.")
                return
            }
            val minIn = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val minOut = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minIn <= 0 || minOut <= 0) {
                updateStatus("This device rejected 48 kHz PCM audio. Audio not started.")
                return
            }
            val bufferBytes = maxOf(minIn, minOut, 4096) * 2
            val inputFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val outputFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val rec = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(inputFormat)
                .setBufferSizeInBytes(bufferBytes)
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                updateStatus("AudioRecord could not initialize.")
                return
            }
            if (!rec.setPreferredDevice(builtInMic)) {
                rec.release()
                updateStatus("Android refused the built-in microphone routing request. Audio not started.")
                return
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(outputFormat)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                rec.release()
                track.release()
                updateStatus("AudioTrack could not initialize.")
                return
            }
            val target = chosenOutput
            if (target != null && !track.setPreferredDevice(target)) {
                rec.release()
                track.release()
                updateStatus("Android refused the Bluetooth output routing request. Refresh outputs and retry.")
                return
            }

            recorder = rec
            player = track
            running.set(true)
            rec.startRecording()
            track.play()
            updateStatus("Running. Requested input: built-in phone mic. Requested output: ${target?.productName ?: "system media route"}. Verify actual routing by testing.")
            worker = thread(name = "HearLinkAudioBridge", isDaemon = true) {
                val samples = ShortArray(1024)
                var reportedRoute = false
                try {
                    while (running.get()) {
                        val count = rec.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                        if (count <= 0) continue
                        if (!reportedRoute) {
                            val actualInput = rec.routedDevice?.let { it.productName.toString() + " (type " + it.type + ")" } ?: "not reported"
                            val actualOutput = track.routedDevice?.let { it.productName.toString() + " (type " + it.type + ")" } ?: "not reported"
                            updateStatus("Android reports input: " + actualInput + "; output: " + actualOutput + ". If input is not the phone mic, stop and check Call audio/profile settings.")
                            reportedRoute = true
                        }
                        var peak = 0
                        for (i in 0 until count) {
                            val raw = samples[i].toInt()
                            peak = maxOf(peak, abs(raw))
                            samples[i] = (raw * gain).toInt().coerceIn(-32768, 32767).toShort()
                        }
                        val written = track.write(samples, 0, count, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) throw IllegalStateException("AudioTrack write error $written")
                        val percentage = (peak * 100 / 32768).coerceIn(0, 100)
                        runOnUiThread { level.text = "Phone-mic input peak: $percentage%" }
                    }
                } catch (e: Exception) {
                    if (running.get()) updateStatus("Audio stopped: ${e.message ?: "audio routing error"}")
                } finally {
                    releaseAudio()
                }
            }
        } catch (e: SecurityException) {
            updateStatus("Permission or Bluetooth access denied: ${e.message}")
            stopBridge()
        } catch (e: Exception) {
            updateStatus("Could not start audio: ${e.message}")
            stopBridge()
        }
    }

    private fun stopBridge() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        try { player?.pause() } catch (_: Exception) {}
        try { player?.flush() } catch (_: Exception) {}
        worker?.interrupt()
        releaseAudio()
        updateStatus("Stopped.")
        level.text = "Phone-mic input peak: —"
    }

    @Synchronized
    private fun releaseAudio() {
        val rec = recorder
        val track = player
        recorder = null
        player = null
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        running.set(false)
    }

    override fun onDestroy() {
        stopBridge()
        super.onDestroy()
    }
}

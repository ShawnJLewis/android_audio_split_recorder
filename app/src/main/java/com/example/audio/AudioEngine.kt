package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.PresetReverb
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEngine(private val context: Context) {

    private val tag = "AudioEngine"

    // Recording States
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    // Playback States
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress

    private val _vocalMeter = MutableStateFlow(0f)
    val vocalMeter: StateFlow<Float> = _vocalMeter

    private val _musicMeter = MutableStateFlow(0f)
    val musicMeter: StateFlow<Float> = _musicMeter

    // Audio Engine Constants
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Two Parallel MediaPlayers for Synchronized Mixer Playback
    private var vocalPlayer: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private var syncJob: Job? = null

    // Native Audio Effects on the Vocal Track
    private var vocalReverb: PresetReverb? = null

    // Track state descriptors
    var currentRecordFile: File? = null
        private set
    var currentVocalsFile: File? = null
        private set
    var currentMusicFile: File? = null
        private set

    init {
        currentRecordFile = File(context.filesDir, "session_raw.wav")
        currentVocalsFile = File(context.filesDir, "session_vocals.wav")
        currentMusicFile = File(context.filesDir, "session_music.wav")
    }

    /**
     * Start high-fidelity audio recording.
     * Instantiates on-device AcousticEchoCanceler (AEC) to dynamically filter speaker outputs.
     */
    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(tag, "Invalid buffer size computed")
            return false
        }

        try {
            // Instantiate AudioRecord from Mic source
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 4
            )

            val sessionId = audioRecord?.audioSessionId ?: 0
            Log.d(tag, "AudioRecord initialized. Session ID: $sessionId")

            // Activate Acoustic Echo Cancellation if available
            if (AcousticEchoCanceler.isAvailable() && sessionId != 0) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)
                echoCanceler?.enabled = true
                Log.d(tag, "Acoustic Echo Canceler (AEC) activated successfully!")
            } else {
                Log.w(tag, "Acoustic Echo Cancellation is not supported/available on this device.")
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            // Stream recorded samples to disk in a separate background job
            recordingJob = coroutineScope.launch {
                val tempFile = File(context.cacheDir, "recording.raw")
                val fos = FileOutputStream(tempFile)
                val data = ByteArray(minBufferSize)

                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(data, 0, data.size) ?: 0
                    if (readBytes > 0) {
                        fos.write(data, 0, readBytes)

                        // Compute immediate amplitude level for our animated fluid bulb visualizer
                        var sum = 0f
                        val shortBuffer = ByteBuffer.wrap(data, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val shortsCount = shortBuffer.remaining()
                        for (i in 0 until shortsCount) {
                            val sample = Math.abs(shortBuffer.get(i).toFloat())
                            sum += sample
                        }
                        if (shortsCount > 0) {
                            val average = sum / shortsCount / 32768f
                            _amplitude.value = (average * 2f).coerceIn(0f, 1f)
                        }
                    }
                    yield()
                }

                fos.close()
                // Package the raw PCM stream into a structured WAV file
                currentRecordFile?.let { outFile ->
                    rawToWav(tempFile, outFile)
                }
                tempFile.delete()
            }

            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting recording: ${e.message}")
            stopRecording()
            return false
        }
    }

    /**
     * Stop the active recording session and discharge effect resources.
     */
    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        _amplitude.value = 0f

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        try {
            echoCanceler?.enabled = false
            echoCanceler?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        echoCanceler = null
    }

    /**
     * Initiates synchronous dual-track mixer playback.
     */
    fun startMixerPlayback(
        vocalVolume: Float,
        musicVolume: Float,
        vocalPan: Float,
        musicPan: Float,
        vocalReverbEnabled: Boolean
    ) {
        if (_isPlaying.value || currentVocalsFile == null || currentMusicFile == null) return

        val vocFile = currentVocalsFile!!
        val musFile = currentMusicFile!!

        if (!vocFile.exists() || !musFile.exists()) {
            Log.e(tag, "Track files are missing. Run separation first.")
            return
        }

        try {
            vocalPlayer = MediaPlayer().apply {
                setDataSource(vocFile.absolutePath)
                prepare()
            }

            musicPlayer = MediaPlayer().apply {
                setDataSource(musFile.absolutePath)
                prepare()
            }

            // Apply panning and volumes
            applyTrackMixing(0, vocalVolume, vocalPan)
            applyTrackMixing(1, musicVolume, musicPan)

            // Setup Preset Reverb on the vocal channel if enabled
            if (vocalReverbEnabled) {
                setupReverbEffect()
            }

            // Trigger playback synchronously at the exact same epoch
            vocalPlayer?.start()
            musicPlayer?.start()
            _isPlaying.value = true

            // Sync keeper job
            startSyncCheck()

        } catch (e: Exception) {
            Log.e(tag, "Mixer playback failed: ${e.message}")
            stopMixerPlayback()
        }
    }

    /**
     * Live mixing variables updater. Allows modifying volume & balance on-the-fly.
     * @param channel 0 for Vocals, 1 for Background Music
     */
    fun applyTrackMixing(channel: Int, volume: Float, pan: Float) {
        val player = if (channel == 0) vocalPlayer else musicPlayer
        if (player == null) return

        // Compute stereo balancing weights using panning ratios
        val left = volume * (1f - pan).coerceIn(0f, 1f)
        val right = volume * (1f + pan).coerceIn(0f, 1f)
        try {
            player.setVolume(left, right)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Toggles reverb effect on vocal track
     */
    fun toggleVocalReverb(enabled: Boolean) {
        val audioSessionId = vocalPlayer?.audioSessionId ?: return
        try {
            if (enabled) {
                vocalReverb?.release()
                vocalReverb = PresetReverb(1, audioSessionId).apply {
                    preset = PresetReverb.PRESET_SMALLROOM
                    this.enabled = true
                }
                vocalPlayer?.attachAuxEffect(vocalReverb?.id ?: 0)
                vocalPlayer?.setAuxEffectSendLevel(1.0f)
            } else {
                vocalReverb?.enabled = false
                vocalReverb?.release()
                vocalReverb = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupReverbEffect() {
        val audioSessionId = vocalPlayer?.audioSessionId ?: return
        try {
            vocalReverb = PresetReverb(1, audioSessionId).apply {
                preset = PresetReverb.PRESET_SMALLROOM
                enabled = true
            }
            vocalPlayer?.attachAuxEffect(vocalReverb?.id ?: 0)
            vocalPlayer?.setAuxEffectSendLevel(1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSyncCheck() {
        syncJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                val vPlayer = vocalPlayer
                val mPlayer = musicPlayer

                if (vPlayer == null || mPlayer == null) {
                    stopMixerPlayback()
                    break
                }

                // If playback completes, terminate session
                if (!vPlayer.isPlaying && !mPlayer.isPlaying) {
                    _isPlaying.value = false
                    _playbackProgress.value = 1f
                    break
                }

                val vPos = vPlayer.currentPosition
                val mPos = mPlayer.currentPosition
                val vDur = vPlayer.duration.coerceAtLeast(1)

                _playbackProgress.value = vPos.toFloat() / vDur

                // If tracks drift by more than 60 milliseconds, trigger dynamic phase adjustment
                val drift = Math.abs(vPos - mPos)
                if (drift > 60) {
                    Log.d(tag, "Audio drift detected: ${drift}ms. Realignment triggered.")
                    if (vPos > mPos) {
                        try { mPlayer.seekTo(vPos) } catch (e: Exception) {}
                    } else {
                        try { vPlayer.seekTo(mPos) } catch (e: Exception) {}
                    }
                }

                // Simulate audio signal levels (volume meters) based on play amplitudes
                val baseProgress = _playbackProgress.value
                val vocalAmp = Math.sin(baseProgress * 43.0).coerceIn(-1.0, 1.0).toFloat() * 0.4f + 0.4f
                val musicAmp = Math.cos(baseProgress * 77.0).coerceIn(-1.0, 1.0).toFloat() * 0.3f + 0.5f

                _vocalMeter.value = if (vPlayer.isPlaying) vocalAmp else 0f
                _musicMeter.value = if (mPlayer.isPlaying) musicAmp else 0f

                delay(120)
            }
        }
    }

    fun stopMixerPlayback() {
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _vocalMeter.value = 0f
        _musicMeter.value = 0f

        syncJob?.cancel()
        syncJob = null

        vocalReverb?.enabled = false
        vocalReverb?.release()
        vocalReverb = null

        try {
            vocalPlayer?.stop()
            vocalPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vocalPlayer = null

        try {
            musicPlayer?.stop()
            musicPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        musicPlayer = null
    }

    fun seekMixer(fraction: Float) {
        val player = vocalPlayer ?: return
        val totalDur = player.duration
        val targetPos = (fraction * totalDur).toInt()
        try {
            vocalPlayer?.seekTo(targetPos)
            musicPlayer?.seekTo(targetPos)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Merges current isolated tracks into a single WAV file applying specified balance controls.
     */
    fun mixAndExport(
        outputFile: File,
        vocalVol: Float,
        musicVol: Float,
        onComplete: (Boolean) -> Unit
    ) {
        val vocFile = currentVocalsFile ?: return
        val musFile = currentMusicFile ?: return

        if (!vocFile.exists() || !musFile.exists()) {
            onComplete(false)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val fVoc = FileInputStream(vocFile)
                val fMus = FileInputStream(musFile)

                val header = ByteArray(44)
                fVoc.read(header) // Skip headers
                fMus.read(header)

                outputFile.parentFile?.mkdirs()
                val fOut = FileOutputStream(outputFile)
                fOut.write(ByteArray(44)) // reserve header space

                val bufferVoc = ByteArray(4096)
                val bufferMus = ByteArray(4096)

                var bytesRead: Int
                while (true) {
                    val read = fVoc.read(bufferVoc)
                    if (read == -1) break
                    bytesRead = read
                    val bytesReadMus = fMus.read(bufferMus, 0, bytesRead)
                    val sampleCount = bytesRead / 2

                    val shortVoc = ShortArray(sampleCount)
                    val shortMus = ShortArray(sampleCount)

                    ByteBuffer.wrap(bufferVoc, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortVoc)
                    if (bytesReadMus > 0) {
                        ByteBuffer.wrap(bufferMus, 0, bytesReadMus).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortMus)
                    }

                    val mergedOut = ShortArray(sampleCount)
                    for (i in 0 until sampleCount) {
                        val vSample = (shortVoc[i] / 32768f) * vocalVol
                        val mSample = (shortMus[i] / 32768f) * musicVol
                        val combined = (vSample + mSample).coerceIn(-1f, 1f)
                        mergedOut[i] = (combined * 32767f).toInt().toShort()
                    }

                    val outBytes = ByteArray(bytesRead)
                    ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mergedOut)
                    fOut.write(outBytes)
                }

                fVoc.close()
                fMus.close()
                fOut.close()

                // Inject WAV file headers
                writeFinalHeader(outputFile, sampleRate, 1)
                launch(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    private fun writeFinalHeader(file: File, sampleRate: Int, channels: Int) {
        val rawDataLength = file.length() - 44
        val totalLength = file.length() - 8
        val byteRate = sampleRate * channels * 2
        val blockAlign = (channels * 2).toShort()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalLength.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(rawDataLength.toInt())

        val fileWrite = java.io.RandomAccessFile(file, "rw")
        fileWrite.seek(0)
        fileWrite.write(header)
        fileWrite.close()
    }

    private fun rawToWav(rawFile: File, wavFile: File) {
        val rawDataLength = rawFile.length()
        val totalLength = rawDataLength + 36
        val byteRate = sampleRate * 1 * 2 // 1 channel * 16-bit
        val blockAlign = (2).toShort()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalLength.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort()) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(rawDataLength.toInt())

        val rawIn = FileInputStream(rawFile)
        wavFile.parentFile?.mkdirs()
        val wavOut = FileOutputStream(wavFile)

        wavOut.write(header)

        val tempBuffer = ByteArray(4096)
        while (true) {
            val bytes = rawIn.read(tempBuffer)
            if (bytes == -1) break
            wavOut.write(tempBuffer, 0, bytes)
        }

        rawIn.close()
        wavOut.close()
    }
}

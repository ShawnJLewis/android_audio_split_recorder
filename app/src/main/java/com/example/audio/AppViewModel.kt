package com.example.audio

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val audioEngine = AudioEngine(context)

    enum class AppState {
        IDLE, RECORDING, PROCESSING, READY_TO_MIX
    }

    // UI State variables
    private val _state = MutableStateFlow(AppState.IDLE)
    val state: StateFlow<AppState> = _state

    private val _activeTab = MutableStateFlow(0) // 0: Record, 1: Mixer, 2: AI Analyst
    val activeTab: StateFlow<Int> = _activeTab

    // Separation Settings
    val separationCrossover = MutableStateFlow(1000f) // 1000 Hz center
    val vocalWidthSetting = MutableStateFlow(0.55f) // Q factor

    private val _separationProgress = MutableStateFlow(0f)
    val separationProgress: StateFlow<Float> = _separationProgress

    // Mixer Settings (Vocal track)
    val vocalVolume = MutableStateFlow(0.85f)
    val vocalPan = MutableStateFlow(0.0f) // -1.0f to 1.0f (L to R)
    val vocalMute = MutableStateFlow(false)
    val vocalSolo = MutableStateFlow(false)
    val vocalReverb = MutableStateFlow(false)

    // Mixer Settings (Background Music track)
    val musicVolume = MutableStateFlow(0.60f)
    val musicPan = MutableStateFlow(0.0f)
    val musicMute = MutableStateFlow(false)
    val musicSolo = MutableStateFlow(false)

    // Session Recordings List
    private val _sessions = MutableStateFlow<List<File>>(emptyList())
    val sessions: StateFlow<List<File>> = _sessions

    // Live meters & waveform visualizations
    val liveMicLevel = audioEngine.amplitude
    val liveVocalPlayMeter = audioEngine.vocalMeter
    val liveMusicPlayMeter = audioEngine.musicMeter
    val playbackProgress = audioEngine.playbackProgress
    val isPlaying = audioEngine.isPlaying
    val isRecording = audioEngine.isRecording

    private val _vocalWaveform = MutableStateFlow<List<Float>>(emptyList())
    val vocalWaveform: StateFlow<List<Float>> = _vocalWaveform

    private val _musicWaveform = MutableStateFlow<List<Float>>(emptyList())
    val musicWaveform: StateFlow<List<Float>> = _musicWaveform

    // AI Sound Analyst Response
    private val _aiAnalysisText = MutableStateFlow("")
    val aiAnalysisText: StateFlow<String> = _aiAnalysisText

    private val _isAnalyzingSpeech = MutableStateFlow(false)
    val isAnalyzingSpeech: StateFlow<Boolean> = _isAnalyzingSpeech

    init {
        loadSavedSessions()
        
        // Listen to active recording changes to update app states automatically
        viewModelScope.launch {
            audioEngine.isRecording.collect { recording ->
                if (recording) {
                    _state.value = AppState.RECORDING
                } else if (_state.value == AppState.RECORDING) {
                    _state.value = AppState.IDLE
                    loadSavedSessions()
                    Toast.makeText(context, "Audio file saved successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadSavedSessions() {
        val rootDir = context.filesDir
        val files = rootDir.listFiles()?.filter { 
            it.name.endsWith(".wav") && !it.name.contains("vocals") && !it.name.contains("music") && !it.name.contains("mixed") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        _sessions.value = files
    }

    /**
     * Start speech recording with on-device Echo Cancellation (Aec) enabled
     */
    fun startRecording() {
        viewModelScope.launch {
            audioEngine.stopMixerPlayback()
            val ok = audioEngine.startRecording()
            if (!ok) {
                Toast.makeText(context, "Microphone access fail or audio busy", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopRecording() {
        audioEngine.stopRecording()
    }

    fun toggleRecord() {
        if (audioEngine.isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * Triggers digital band crossover separation.
     * Computes split files and extracts low-res waveforms to update the visualizer.
     */
    fun processAndSeparate() {
        val rawFile = audioEngine.currentRecordFile
        if (rawFile == null || !rawFile.exists()) {
            Toast.makeText(context, "Please record some audio first!", Toast.LENGTH_SHORT).show()
            return
        }

        _state.value = AppState.PROCESSING
        _separationProgress.value = 0f

        viewModelScope.launch {
            val vocOutputFile = audioEngine.currentVocalsFile!!
            val musOutputFile = audioEngine.currentMusicFile!!

            val success = withContext(Dispatchers.Default) {
                DspSeparator.performSeparation(
                    sourceWav = rawFile,
                    vocalsWav = vocOutputFile,
                    musicWav = musOutputFile,
                    crossoverCenter = separationCrossover.value,
                    voiceWidth = vocalWidthSetting.value,
                    onProgress = { progress ->
                        _separationProgress.value = progress
                    }
                )
            }

            if (success) {
                // Generate authentic visualizer points to draw on waveforms
                _vocalWaveform.value = DspSeparator.extractWaveform(vocOutputFile, 75)
                _musicWaveform.value = DspSeparator.extractWaveform(musOutputFile, 75)
                
                _state.value = AppState.READY_TO_MIX
                _activeTab.value = 1 // transition to mixer tab on success!
                Toast.makeText(context, "Tracks separated in high resolution!", Toast.LENGTH_SHORT).show()

                // Trigger AI analysis in the background automatically
                requestAiAnalysis()
            } else {
                _state.value = AppState.IDLE
                Toast.makeText(context, "Audio separation failed. Try recording again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleMixerPlayback() {
        if (audioEngine.isPlaying.value) {
            audioEngine.stopMixerPlayback()
        } else {
            val vVol = if (vocalMute.value) 0f else if (musicSolo.value) 0f else vocalVolume.value
            val mVol = if (musicMute.value) 0f else if (vocalSolo.value) 0f else musicVolume.value
            
            audioEngine.startMixerPlayback(
                vocalVolume = vVol,
                musicVolume = mVol,
                vocalPan = vocalPan.value,
                musicPan = musicPan.value,
                vocalReverbEnabled = vocalReverb.value
            )
        }
    }

    fun stopMixerPlayback() {
        audioEngine.stopMixerPlayback()
    }

    fun seekPlayback(progress: Float) {
        audioEngine.seekMixer(progress)
    }

    fun updateVocalVolume(vol: Float) {
        vocalVolume.value = vol
        val vVol = if (vocalMute.value) 0f else if (musicSolo.value) 0f else vol
        audioEngine.applyTrackMixing(0, vVol, vocalPan.value)
    }

    fun updateVocalPan(pan: Float) {
        vocalPan.value = pan
        val vVol = if (vocalMute.value) 0f else if (musicSolo.value) 0f else vocalVolume.value
        audioEngine.applyTrackMixing(0, vVol, pan)
    }

    fun toggleVocalMute() {
        vocalMute.value = !vocalMute.value
        updateMixerVolumes()
    }

    fun toggleVocalSolo() {
        vocalSolo.value = !vocalSolo.value
        if (vocalSolo.value) musicSolo.value = false
        updateMixerVolumes()
    }

    fun toggleVocalReverb() {
        vocalReverb.value = !vocalReverb.value
        audioEngine.toggleVocalReverb(vocalReverb.value)
    }

    fun updateMusicVolume(vol: Float) {
        musicVolume.value = vol
        val mVol = if (musicMute.value) 0f else if (vocalSolo.value) 0f else vol
        audioEngine.applyTrackMixing(1, mVol, musicPan.value)
    }

    fun updateMusicPan(pan: Float) {
        musicPan.value = pan
        val mVol = if (musicMute.value) 0f else if (vocalSolo.value) 0f else musicVolume.value
        audioEngine.applyTrackMixing(1, mVol, pan)
    }

    fun toggleMusicMute() {
        musicMute.value = !musicMute.value
        updateMixerVolumes()
    }

    fun toggleMusicSolo() {
        musicSolo.value = !musicSolo.value
        if (musicSolo.value) vocalSolo.value = false
        updateMixerVolumes()
    }

    private fun updateMixerVolumes() {
        val vVol = if (vocalMute.value) 0f else if (musicSolo.value) 0f else vocalVolume.value
        val mVol = if (musicMute.value) 0f else if (vocalSolo.value) 0f else musicVolume.value
        
        audioEngine.applyTrackMixing(0, vVol, vocalPan.value)
        audioEngine.applyTrackMixing(1, mVol, musicPan.value)
    }

    /**
     * Executes compile-mix & exports to a single high-fidelity WAV on disk.
     */
    fun exportMixedSession(onComplete: (File?) -> Unit) {
        val exportFile = File(context.filesDir, "mix_export_${System.currentTimeMillis()}.wav")
        val vVol = if (vocalMute.value) 0f else if (musicSolo.value) 0f else vocalVolume.value
        val mVol = if (musicMute.value) 0f else if (vocalSolo.value) 0f else musicVolume.value

        audioEngine.mixAndExport(exportFile, vVol, mVol) { ok ->
            if (ok) {
                loadSavedSessions()
                onComplete(exportFile)
            } else {
                onComplete(null)
            }
        }
    }

    /**
     * Request detailed audio engineering telemetry analysis from the Gemini REST model.
     */
    fun requestAiAnalysis() {
        _isAnalyzingSpeech.value = true
        _aiAnalysisText.value = ""

        viewModelScope.launch {
            val recordLength = audioEngine.currentRecordFile?.length() ?: 0L
            val guessedDuration = (recordLength / (44100 * 2)).coerceAtLeast(3).toInt()

            val response = GeminiSoundAnalyst.analyzeAudioProfile(
                durationSec = guessedDuration,
                averageVolume = liveMicLevel.value.coerceAtLeast(0.12f),
                voiceFrequencyHz = 1000f - (separationCrossover.value - 1000f) * 0.4f,
                crossoverCenter = separationCrossover.value,
                noiseLevelDb = 22f + (1f - vocalWidthSetting.value) * 12f
            )
            _aiAnalysisText.value = response
            _isAnalyzingSpeech.value = false
        }
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopMixerPlayback()
        audioEngine.stopRecording()
    }
}

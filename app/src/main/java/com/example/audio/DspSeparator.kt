package com.example.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-fidelity Digital Signal Processing (DSP) Audio Separation Engine.
 * Implements second-order IIR Biquad Filters in pure Kotlin to separate
 * vocal speech bands from background music/instrumentals, generating two native track files.
 */
object DspSeparator {

    /**
     * Biquad Filter Coefficients and State
     */
    class BiquadFilter(
        private val type: FilterType,
        private val sampleRate: Float,
        private val centerFreq: Float,
        private val q: Float,
        private val gainDb: Float = 0f
    ) {
        enum class FilterType {
            LOWPASS, HIGHPASS, BANDPASS, NOTCH, PEAK
        }

        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var a0 = 0f
        private var a1 = 0f
        private var a2 = 0f

        // Filter state history (delays)
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            calculateCoefficients()
        }

        private fun calculateCoefficients() {
            val w0 = (2f * PI * centerFreq / sampleRate).toFloat()
            val alpha = (sin(w0) / (2f * q)).toFloat()
            val a = sqrt(10f.pow(gainDb / 40f))

            when (type) {
                FilterType.LOWPASS -> {
                    b0 = (1f - cos(w0)) / 2f
                    b1 = 1f - cos(w0)
                    b2 = (1f - cos(w0)) / 2f
                    a0 = 1f + alpha
                    a1 = -2f * cos(w0)
                    a2 = 1f - alpha
                }
                FilterType.HIGHPASS -> {
                    b0 = (1f + cos(w0)) / 2f
                    b1 = -(1f + cos(w0))
                    b2 = (1f + cos(w0)) / 2f
                    a0 = 1f + alpha
                    a1 = -2f * cos(w0)
                    a2 = 1f - alpha
                }
                FilterType.BANDPASS -> {
                    b0 = alpha
                    b1 = 0f
                    b2 = -alpha
                    a0 = 1f + alpha
                    a1 = -2f * cos(w0)
                    a2 = 1f - alpha
                }
                FilterType.NOTCH -> {
                    b0 = 1f
                    b1 = -2f * cos(w0)
                    b2 = 1f
                    a0 = 1f + alpha
                    a1 = -2f * cos(w0)
                    a2 = 1f - alpha
                }
                FilterType.PEAK -> {
                    val gAlpha = alpha * a
                    b0 = 1f + gAlpha
                    b1 = -2f * cos(w0)
                    b2 = 1f - gAlpha
                    val dAlpha = alpha / a
                    a0 = 1f + dAlpha
                    a1 = -2f * cos(w0)
                    a2 = 1f - dAlpha
                }
            }

            // Normalize coefficients by a0
            b0 /= a0
            b1 /= a0
            b2 /= a0
            a1 /= a0
            a2 /= a0
        }

        fun process(sample: Float): Float {
            val output = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            
            // Unbounded buffer protection
            var safeOutput = output
            if (safeOutput.isNaN() || safeOutput.isInfinite()) {
                safeOutput = 0f
                reset()
            } else {
                safeOutput = safeOutput.coerceIn(-1f, 1f)
            }

            x2 = x1
            x1 = sample
            y2 = y1
            y1 = safeOutput

            return safeOutput
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        private fun Float.pow(exp: Float): Float {
            return Math.pow(this.toDouble(), exp.toDouble()).toFloat()
        }
    }

    /**
     * Separates vocals and music from a recorded 16-bit PCM WAV file.
     * Generates two separate high-quality WAV files with standard 44-byte headers.
     * @param sourceWav Original recorded file.
     * @param vocalsWav Output path for isolated voice track.
     * @param musicWav Output path for separated backing track.
     * @param crossoverCenter Crossover split center frequency (typically 1200 Hz).
     * @param voiceWidth Speech bandpass width descriptor (lower Q means wider vocals).
     */
    fun performSeparation(
        sourceWav: File,
        vocalsWav: File,
        musicWav: File,
        crossoverCenter: Float = 1000f,
        voiceWidth: Float = 0.5f,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (!sourceWav.exists() || sourceWav.length() <= 44) return false

        try {
            val fileIn = FileInputStream(sourceWav)
            val header = ByteArray(44)
            fileIn.read(header) // Skip or copy original WAV header

            // Parse sample rate from WAV header (bytes 24-27)
            val sampleRateRaw = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val sampleRate = if (sampleRateRaw > 0) sampleRateRaw.toFloat() else 44100f
            val numChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            val totalDataSize = sourceWav.length() - 44
            val bufferSize = 1024 * 16 // 16KB chunk size
            val buffer = ByteArray(bufferSize)

            // Ensure destination directories exist
            vocalsWav.parentFile?.mkdirs()
            musicWav.parentFile?.mkdirs()

            // Initialize output streams with placeholder headers
            val vocalsOut = FileOutputStream(vocalsWav)
            val musicOut = FileOutputStream(musicWav)
            vocalsOut.write(ByteArray(44))
            musicOut.write(ByteArray(44))

            // Initialize professional DSP separation filters
            // Track 1 (Vocal Isolator): Bandpass filter focusing on speech frequency band (150Hz to 3400Hz)
            val voiceLowCut = BiquadFilter(BiquadFilter.FilterType.HIGHPASS, sampleRate, 130f, 0.707f)
            val voiceHighCut = BiquadFilter(BiquadFilter.FilterType.LOWPASS, sampleRate, 3200f, 0.707f)
            // Mid-range speech boost peak filter to maximize vocal clarity
            val vocalBooster = BiquadFilter(BiquadFilter.FilterType.PEAK, sampleRate, 1000f, voiceWidth, 4f)

            // Track 2 (Background Music / Vocal Suppressor): Notch filter at speech band + low-frequency boost and high-frequency sparkle boost
            val voiceSuppressor = BiquadFilter(BiquadFilter.FilterType.NOTCH, sampleRate, crossoverCenter, 0.55f)
            val bassBooster = BiquadFilter(BiquadFilter.FilterType.LOWPASS, sampleRate, 110f, 0.8f) // capture instruments and subbass
            val sparkleBooster = BiquadFilter(BiquadFilter.FilterType.HIGHPASS, sampleRate, 4500f, 0.7f) // capture details & cymbals

            var bytesRead: Int
            var totalBytesProcessed = 0L

            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                val samplesCount = bytesRead / 2 // 16-bit audio = 2 bytes per sample
                val rawSamples = ShortArray(samplesCount)
                
                // Convert bytes to 16-bit linear PCM Shorts
                ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(rawSamples)

                val vocalOutputShorts = ShortArray(samplesCount)
                val musicOutputShorts = ShortArray(samplesCount)

                for (i in 0 until samplesCount) {
                    val inputSample = rawSamples[i] / 32768f // Normalize to [-1.0f, 1.0f]

                    // 1. Process Vocal Track
                    val processedVocal1 = voiceLowCut.process(inputSample)
                    val processedVocal2 = voiceHighCut.process(processedVocal1)
                    val finalVocal = vocalBooster.process(processedVocal2)

                    // 2. Process Background Music Track
                    // Subtracted vocal range from overall audio and recombined original instrumentals
                    val notchFiltered = voiceSuppressor.process(inputSample)
                    val subBassValue = bassBooster.process(inputSample) * 0.4f
                    val highsValue = sparkleBooster.process(inputSample) * 0.3f
                    val finalMusic = (notchFiltered * 0.9f + subBassValue + highsValue).coerceIn(-1f, 1f)

                    // Convert normalized Floats back to 16-bit linear PCM Shorts
                    vocalOutputShorts[i] = (finalVocal * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                    musicOutputShorts[i] = (finalMusic * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // Convert processed Shorts back to little-endian byte buffers
                val vocalBytes = ByteArray(bytesRead)
                ByteBuffer.wrap(vocalBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(vocalOutputShorts)

                val musicBytes = ByteArray(bytesRead)
                ByteBuffer.wrap(musicBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(musicOutputShorts)

                // Write to separate paths
                vocalsOut.write(vocalBytes, 0, bytesRead)
                musicOut.write(musicBytes, 0, bytesRead)

                totalBytesProcessed += bytesRead
                val progress = (totalBytesProcessed.toFloat() / totalDataSize).coerceIn(0f, 1f)
                onProgress(progress)
            }

            fileIn.close()
            vocalsOut.close()
            musicOut.close()

            // Write matching WAV standard headers to both processed files
            writeWavHeader(vocalsWav, sampleRate.toInt(), numChannels)
            writeWavHeader(musicWav, sampleRate.toInt(), numChannels)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Writes 44-byte standard RIFF Wave header over the pre-allocated bytes of a PCM file.
     */
    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int) {
        val rawDataLength = file.length() - 44
        val totalLength = file.length() - 8
        val byteRate = sampleRate * channels * 2
        val blockAlign = (channels * 2).toShort()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)) // 0..3
        buffer.putInt(totalLength.toInt())                // 4..7 (ChunkSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII)) // 8..11 (Format)
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII)) // 12..15 (Subchunk1ID)
        buffer.putInt(16)                                  // 16..19 (Subchunk1Size)
        buffer.putShort(1.toShort())                      // 20..21 (AudioFormat = Linear PCM)
        buffer.putShort(channels.toShort())               // 22..23 (NumChannels)
        buffer.putInt(sampleRate)                         // 24..27 (SampleRate)
        buffer.putInt(byteRate)                            // 28..31 (ByteRate)
        buffer.putShort(blockAlign)                       // 32..33 (BlockAlign)
        buffer.putShort(16.toShort())                     // 34..35 (BitsPerSample = 16-bit)
        buffer.put("data".toByteArray(Charsets.US_ASCII)) // 36..39 (Subchunk2ID)
        buffer.putInt(rawDataLength.toInt())              // 40..43 (Subchunk2Size)

        // Write WAV header at file offset 0
        val fileStream = FileOutputStream(file, true) // open file
        val fileWrite = java.io.RandomAccessFile(file, "rw")
        fileWrite.seek(0)
        fileWrite.write(header)
        fileWrite.close()
    }

    /**
     * Extracts low-resolution waveform data points from a WAV file for UI display.
     * Highly optimized, reads wav in uniform intervals.
     */
    fun extractWaveform(audioFile: File, pointsCount: Int = 100): List<Float> {
        if (!audioFile.exists() || audioFile.length() <= 44) {
            return List(pointsCount) { 0.1f }
        }

        val result = mutableListOf<Float>()
        try {
            val fileLength = audioFile.length() - 44
            val sampleCount = fileLength / 2
            val step = (sampleCount / pointsCount).coerceAtLeast(1).toInt()

            val fileIn = FileInputStream(audioFile)
            fileIn.skip(44) // Skip header

            val buffer = ByteArray(2048)
            var currentStreamIndex = 0L

            for (p in 0 until pointsCount) {
                val byteOffset = 44 + (p * step * 2)
                if (byteOffset >= audioFile.length()) {
                    result.add(0.02f)
                    continue
                }

                val targetSkip = byteOffset - currentStreamIndex - 44
                if (targetSkip > 0) {
                    fileIn.skip(targetSkip)
                    currentStreamIndex += targetSkip
                }

                val bytesRead = fileIn.read(buffer, 0, 2)
                if (bytesRead == 2) {
                    currentStreamIndex += 2
                    val sample = ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short
                    val normalized = Math.abs(sample.toInt()) / 32768f
                    result.add(normalized.coerceIn(0.01f, 1f))
                } else {
                    result.add(0.02f)
                }
            }
            fileIn.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return List(pointsCount) { 0.1f }
        }

        // Return a minimum bar height for pleasant visuals
        return result.map { if (it < 0.05f) 0.05f else it }
    }
}

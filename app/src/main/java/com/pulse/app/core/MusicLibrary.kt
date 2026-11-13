package com.pulse.app.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class Song(
    val id: String,
    val title: String,
    val path: String,
    val durationMs: Long,
    val fps: Int = 20
)

/**
 * 极简本地曲库：导入 → 复制到私有目录 → 解码出“激昂程度”包络 → 播放时按进度取值。
 * 包络存在同目录的 .env 文件里，第二次播放不用重新分析。
 */
class MusicLibrary(context: Context) {

    private val app = context.applicationContext
    private val dir: File by lazy {
        File(app.filesDir, "songs").apply { mkdirs() }
    }

    fun list(): List<Song> =
        dir.listFiles { f -> f.extension == "audio" }
            ?.mapNotNull { f ->
                val id = f.nameWithoutExtension
                val env = envelopeFile(id)
                if (!env.exists()) return@mapNotNull null
                Song(
                    id = id,
                    title = readTitle(id) ?: id,
                    path = f.absolutePath,
                    durationMs = readDuration(f),
                )
            }
            ?.sortedBy { it.title }
            ?: emptyList()

    fun import(uri: Uri): Song? {
        val name = queryName(uri) ?: "song"
        val id = System.currentTimeMillis().toString()
        val target = File(dir, "$id.audio")
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        saveTitle(id, name.substringBeforeLast('.').take(40))
        return Song(
            id = id,
            title = readTitle(id) ?: name,
            path = target.absolutePath,
            durationMs = readDuration(target)
        )
    }

    /** 调试用：直接从文件路径导入 */
    fun importFromPath(path: String): Song? {
        val src = File(path)
        if (!src.exists()) return null
        val id = System.currentTimeMillis().toString()
        val target = File(dir, "$id.audio")
        runCatching {
            src.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }.onFailure { return null }
        saveTitle(id, src.nameWithoutExtension.take(40))
        return Song(
            id = id,
            title = readTitle(id) ?: src.nameWithoutExtension,
            path = target.absolutePath,
            durationMs = readDuration(target)
        )
    }

    fun delete(song: Song) {
        runCatching { File(song.path).delete() }
        runCatching { envelopeFile(song.id).delete() }
        runCatching { titleFile(song.id).delete() }
    }

    fun envelope(song: Song): FloatArray {
        val f = envelopeFile(song.id)
        if (f.exists()) {
            runCatching {
                DataInputStream(f.inputStream().buffered()).use { input ->
                    val n = input.readInt()
                    if (n <= 0 || n > 2_000_000) return@runCatching FloatArray(0)
                    FloatArray(n) { input.readFloat() }
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return FloatArray(0)
    }

    suspend fun analyze(song: Song): FloatArray = withContext(Dispatchers.Default) {
        val env = decodeEnvelope(song.path)
        if (env.isNotEmpty()) {
            runCatching {
                DataOutputStream(envelopeFile(song.id).outputStream().buffered()).use { out ->
                    out.writeInt(env.size)
                    env.forEach { out.writeFloat(it) }
                }
            }
        }
        env
    }

    // ------------------------------------------------------------- 解码 & 分析
    private fun decodeEnvelope(path: String): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return FloatArray(0)
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
            var channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
            var frameSamples = (sampleRate * 0.05f).toInt().coerceAtLeast(256) * channels

            val rmsList = ArrayList<Float>(2048)
            val bassList = ArrayList<Float>(2048)
            val fluxList = ArrayList<Float>(2048)

            var rmsAcc = 0.0
            var bassAcc = 0.0
            var fluxAcc = 0.0
            var count = 0
            var prev = 0f
            var lp = 0f

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var guard = 0

            while (!outputDone && guard < 2_000_000) {
                guard++
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)
                        val size = if (ib != null) extractor.readSampleData(ib, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = runCatching { f.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(sampleRate)
                        channels = runCatching { f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(channels)
                        frameSamples = (sampleRate * 0.05f).toInt().coerceAtLeast(256) * channels
                    }

                    outIdx >= 0 -> {
                        if (info.size > 0) {
                            val ob = codec.getOutputBuffer(outIdx)
                            if (ob != null && info.size >= 2) {
                                ob.position(info.offset)
                                ob.limit(info.offset + info.size)
                                ob.order(ByteOrder.nativeOrder())
                                while (ob.remaining() >= 2) {
                                    val s = ob.short / 32768f
                                    rmsAcc += (s * s).toDouble()
                                    lp += 0.05f * (s - lp)
                                    bassAcc += (lp * lp).toDouble()
                                    fluxAcc += abs(s - prev).toDouble()
                                    prev = s
                                    count++
                                    if (count >= frameSamples) {
                                        val n = count.toFloat()
                                        rmsList.add(sqrt(rmsAcc / n).toFloat())
                                        bassList.add(sqrt(bassAcc / n).toFloat())
                                        fluxList.add((fluxAcc / n).toFloat())
                                        rmsAcc = 0.0
                                        bassAcc = 0.0
                                        fluxAcc = 0.0
                                        count = 0
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }

            if (rmsList.size < 2) return FloatArray(0)
            val refRms = percentile(rmsList, 0.95f).coerceAtLeast(0.001f)
            val refBass = percentile(bassList, 0.95f).coerceAtLeast(0.0005f)
            val refFlux = percentile(fluxList, 0.95f).coerceAtLeast(0.0005f)

            val out = FloatArray(rmsList.size)
            var smooth = 0f
            for (i in rmsList.indices) {
                val loud = (rmsList[i] / refRms).coerceIn(0f, 1f)
                val bass = (bassList[i] / refBass).coerceIn(0f, 1f)
                val flux = (fluxList[i] / refFlux).coerceIn(0f, 1f)
                val raw = (0.58f * loud + 0.22f * bass + 0.20f * flux).coerceIn(0f, 1f)
                smooth = if (raw > smooth) smooth + (raw - smooth) * 0.55f
                else smooth + (raw - smooth) * 0.16f
                out[i] = smooth
            }
            out
        } catch (e: Exception) {
            FloatArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun percentile(list: List<Float>, p: Float): Float {
        if (list.isEmpty()) return 0f
        val sorted = list.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun envelopeFile(id: String) = File(dir, "$id.env")
    private fun titleFile(id: String) = File(dir, "$id.title")

    private fun saveTitle(id: String, title: String) {
        runCatching { titleFile(id).writeText(title) }
    }

    private fun readTitle(id: String): String? =
        runCatching { titleFile(id).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun readDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryName(uri: Uri): String? {
        return runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    /** 供 UI 显示“激昂程度”用的 dB 近似，仅用于调试 */
    fun levelToDb(v: Float): Float = 20f * ln(v.coerceAtLeast(0.0001f)) / ln(10f)
}

package com.funny.aitoy.core.log

import com.funny.aitoy.core.cache.CacheManager
import com.funny.aitoy.core.cache.fileSubDir
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun createPlatformLogSinks(): List<LogSink> =
    listOf(JvmConsoleLogSink, JvmHourlyFileLogSink)

private object JvmConsoleLogSink : LogSink {
    override fun log(event: LogEvent) {
        val line = buildLine(event, includeThrowable = false)
        if (event.level.priority >= LogLevel.ERROR.priority) {
            System.err.println(line)
            event.throwable?.printStackTrace(System.err)
        } else {
            println(line)
            event.throwable?.printStackTrace(System.out)
        }
    }
}

private object JvmHourlyFileLogSink : LogSink {
    private val lock = Any()
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val hourFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH", Locale.getDefault())
    private val lineTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private const val MAX_BUFFERED_LINE = 20

    private val logDir: File by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CacheManager.fileSubDir("logs")
    }

    private var currentHourKey: String? = null
    private var writer: BufferedWriter? = null
    private val lineBuffer = ArrayList<String>(MAX_BUFFERED_LINE)

    override fun log(event: LogEvent) {
        val instant = Instant.ofEpochMilli(event.timestampMs)
        val dateTime = instant.atZone(zoneId)
        val hourKey = dateTime.format(hourFormatter)
        val line = buildLine(event, lineTimeFormatter.format(dateTime), includeThrowable = true)

        synchronized(lock) {
            rotateIfNeeded(hourKey)
            lineBuffer += line
            if (lineBuffer.size >= MAX_BUFFERED_LINE || event.level.priority >= LogLevel.ERROR.priority) {
                flushLocked()
            }
        }
    }

    private fun rotateIfNeeded(hourKey: String) {
        if (currentHourKey == hourKey && writer != null) return
        flushLocked()
        writer?.closeQuietly()
        writer = openWriter(hourKey)
        currentHourKey = hourKey
    }

    private fun openWriter(hourKey: String): BufferedWriter {
        val file = logDir.resolve("aitoy-$hourKey.log")
        file.parentFile?.mkdirs()
        return BufferedWriter(FileWriter(file, true), 16 * 1024)
    }

    private fun flushLocked() {
        val localWriter = writer ?: return
        if (lineBuffer.isEmpty()) return
        runCatching {
            lineBuffer.forEach { line ->
                localWriter.write(line)
                localWriter.newLine()
            }
            localWriter.flush()
            lineBuffer.clear()
        }.onFailure {
            System.err.println("[Log] 写入文件日志失败: ${it.message}")
            lineBuffer.clear()
        }
    }

    private fun BufferedWriter.closeQuietly() {
        runCatching { close() }
    }
}

private fun buildLine(
    event: LogEvent,
    timestampText: String? = null,
    includeThrowable: Boolean,
): String {
    val timeText = timestampText ?: formatJvmLogTime(event.timestampMs)
    val base = "$timeText ${event.level.shortName()}/${event.tag}: ${event.message}"
    if (!includeThrowable || event.throwable == null) return base
    return base + "\n" + event.throwable.stackTraceToText()
}

private fun formatJvmLogTime(timestampMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun LogLevel.shortName(): String = when (this) {
    LogLevel.VERBOSE -> "V"
    LogLevel.DEBUG -> "D"
    LogLevel.INFO -> "I"
    LogLevel.WARN -> "W"
    LogLevel.ERROR -> "E"
}

private fun Throwable.stackTraceToText(): String {
    val stringWriter = StringWriter()
    PrintWriter(stringWriter).use { printWriter ->
        printStackTrace(printWriter)
    }
    return stringWriter.toString().trimEnd()
}

package com.funny.aitoy.core.log

import com.funny.aitoy.core.utils.nowMs

enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
}

data class LogEvent(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val timestampMs: Long = nowMs(),
)

fun interface LogSink {
    fun log(event: LogEvent)
}

internal expect fun createPlatformLogSinks(): List<LogSink>

object Log {
    @Volatile
    var minLevel: LogLevel = LogLevel.DEBUG

    @Volatile
    private var sinks: List<LogSink> = createPlatformLogSinks()

    fun d(tag: String, message: () -> String) = log(LogLevel.DEBUG, tag, null, message)

    fun d(tag: String, throwable: Throwable, message: () -> String) =
        log(LogLevel.DEBUG, tag, throwable, message)

    fun i(tag: String, message: () -> String) = log(LogLevel.INFO, tag, null, message)

    fun i(tag: String, throwable: Throwable, message: () -> String) =
        log(LogLevel.INFO, tag, throwable, message)

    fun w(tag: String, message: () -> String) = log(LogLevel.WARN, tag, null, message)

    fun w(tag: String, throwable: Throwable, message: () -> String) =
        log(LogLevel.WARN, tag, throwable, message)

    fun e(tag: String, message: () -> String) = log(LogLevel.ERROR, tag, null, message)

    fun e(tag: String, throwable: Throwable, message: () -> String) =
        log(LogLevel.ERROR, tag, throwable, message)

    fun v(tag: String, message: () -> String) = log(LogLevel.VERBOSE, tag, null, message)

    fun v(tag: String, throwable: Throwable, message: () -> String) =
        log(LogLevel.VERBOSE, tag, throwable, message)

    fun addSink(sink: LogSink) {
        sinks = sinks + sink
    }

    fun removeSink(sink: LogSink) {
        sinks = sinks.filterNot { it === sink }
    }

    fun replaceSinks(newSinks: List<LogSink>) {
        sinks = newSinks.toList()
    }

    fun resetPlatformSinks() {
        sinks = createPlatformLogSinks()
    }

    private fun log(
        level: LogLevel,
        tag: String,
        throwable: Throwable?,
        message: () -> String,
    ) {
        if (level.priority < minLevel.priority) return

        val resolvedMessage = runCatching(message).getOrElse {
            "日志消息构建失败：${it.message ?: it::class.simpleName.orEmpty()}"
        }
        val event = LogEvent(
            level = level,
            tag = tag,
            message = resolvedMessage,
            throwable = throwable,
        )

        sinks.forEach { sink ->
            runCatching { sink.log(event) }
        }
    }
}

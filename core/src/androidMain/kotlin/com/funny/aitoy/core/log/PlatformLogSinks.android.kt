package com.funny.aitoy.core.log

import android.util.Log as AndroidLog

internal actual fun createPlatformLogSinks(): List<LogSink> = listOf(AndroidLogSink, RecentLogBuffer)

private object AndroidLogSink : LogSink {
    override fun log(event: LogEvent) {
        when (event.level) {
            LogLevel.VERBOSE -> AndroidLog.v(event.tag, event.message, event.throwable)
            LogLevel.DEBUG -> AndroidLog.d(event.tag, event.message, event.throwable)
            LogLevel.INFO -> AndroidLog.i(event.tag, event.message, event.throwable)
            LogLevel.WARN -> AndroidLog.w(event.tag, event.message, event.throwable)
            LogLevel.ERROR -> AndroidLog.e(event.tag, event.message, event.throwable)
        }
    }
}

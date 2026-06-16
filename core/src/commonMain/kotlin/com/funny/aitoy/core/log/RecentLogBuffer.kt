package com.funny.aitoy.core.log

object RecentLogBuffer : LogSink {
    private const val LIMIT = 100
    private val lock = Any()
    private val lines = ArrayDeque<String>(LIMIT)

    override fun log(event: LogEvent) {
        val line = buildString {
            append(event.timestampMs)
            append(' ')
            append(event.level.name.first())
            append('/')
            append(event.tag)
            append(": ")
            append(event.message)
            event.throwable?.message?.let {
                append(" | ")
                append(it)
            }
        }
        synchronized(lock) {
            while (lines.size >= LIMIT) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }
}

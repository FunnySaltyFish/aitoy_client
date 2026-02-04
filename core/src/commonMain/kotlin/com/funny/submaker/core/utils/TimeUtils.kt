package com.funny.submaker.core.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.getOrSet

object TimeUtils {
    fun formatTime(
        time: Long,
        formatTemplate: String = "%4d-%02d-%02d %02d:%02d:%02d",
    ): String {
        val date = Date(time)
        val calendar = Calendar.getInstance()
        calendar.time = date
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        return formatTemplate.format(year, month, day, hour, minute, second)
    }

    fun getNowStr(): String = formatTime(System.currentTimeMillis())

    fun getNowStrUnderline(): String =
        formatTime(System.currentTimeMillis(), "%4d_%02d_%02d_%02d_%02d_%02d")
}

fun nowMs(): Long = System.currentTimeMillis()

fun Date.format(template: String = "%4d-%02d-%02d %02d:%02d:%02d"): String =
    TimeUtils.formatTime(time, template)

private val isoDateFormat = ThreadLocal<SimpleDateFormat>()

fun Date.toISOString(): String {
    return isoDateFormat.getOrSet {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }.format(this)
}

fun Long.toDate(): Date = Date(this)


package com.funny.aitoy.core.utils

@JvmInline
value class FileSize(val bytes: Long) {
    override fun toString(): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
            else -> "${bytes / 1024 / 1024 / 1024} GB"
        }
    }

    companion object {
        fun fromBytes(bytes: Long): FileSize = FileSize(bytes)
        fun fromKilobytes(kilobytes: Long): FileSize = FileSize(kilobytes * 1024)
        fun fromMegabytes(megabytes: Long): FileSize = FileSize(megabytes * 1024 * 1024)
        fun fromGigabytes(gigabytes: Long): FileSize = FileSize(gigabytes * 1024 * 1024 * 1024)
    }
}

fun Int.bytes(): FileSize = FileSize.fromBytes(toLong())

val Int.MB: FileSize
    get() = FileSize.fromMegabytes(toLong())


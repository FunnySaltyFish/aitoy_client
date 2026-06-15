package com.funny.aitoy.core.cache

import java.io.File

expect object CacheManager {
    val cacheDir: File
    val fileDir: File
}

fun CacheManager.cacheSubDir(name: String): File = cacheDir.resolve(name).ensureDirectory()

fun CacheManager.fileSubDir(name: String): File = fileDir.resolve(name).ensureDirectory()

fun CacheManager.cacheSizeBytes(): Long = cacheDir.dirSizeBytes()

fun CacheManager.fileSizeBytes(): Long = fileDir.dirSizeBytes()

fun CacheManager.totalSizeBytes(): Long = cacheSizeBytes() + fileSizeBytes()

fun CacheManager.clearCache(subDir: String? = null): Long = clearDir(cacheDir, subDir)

fun CacheManager.clearFiles(subDir: String? = null): Long = clearDir(fileDir, subDir)

private fun clearDir(rootDir: File, subDir: String?): Long {
    val target = subDir?.takeIf { it.isNotBlank() }?.let { rootDir.resolve(it) } ?: rootDir
    if (!target.exists()) return 0L
    val size = target.dirSizeBytes()
    if (target == rootDir) {
        target.listFiles().orEmpty().forEach { child ->
            child.deleteRecursivelySafe()
        }
    } else {
        target.deleteRecursivelySafe()
        target.mkdirs()
    }
    return size
}

private fun File.dirSizeBytes(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { it.dirSizeBytes() }
}

private fun File.ensureDirectory(): File {
    if (!exists()) mkdirs()
    return this
}

private fun File.deleteRecursivelySafe(): Boolean {
    if (!exists()) return true
    val childrenDeleted = listFiles().orEmpty().all { it.deleteRecursivelySafe() }
    return childrenDeleted && delete()
}

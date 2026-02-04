package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class DataSaverProperties(
    private val filePath: String,
) : DataSaverInterface() {
    private val properties = Properties()

    init {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        if (file.exists()) {
            runCatching {
                FileInputStream(file).use(properties::load)
            }
        }
    }

    private fun flush() {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            properties.store(output, null)
        }
    }

    override fun <T> saveData(key: String, data: T) {
        if (data == null) {
            remove(key)
            return
        }
        properties[key] = data.toString()
        flush()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> readData(key: String, default: T): T {
        val value = properties.getProperty(key) ?: return default
        return when (default) {
            is Int -> value.toIntOrNull() ?: default
            is Long -> value.toLongOrNull() ?: default
            is Boolean -> value.toBooleanStrictOrNull() ?: default
            is Double -> value.toDoubleOrNull() ?: default
            is Float -> value.toFloatOrNull() ?: default
            is String -> value
            else -> throwError("read", default)
        } as T
    }

    override fun remove(key: String) {
        properties.remove(key)
        flush()
    }

    override fun contains(key: String): Boolean = properties.containsKey(key)
}


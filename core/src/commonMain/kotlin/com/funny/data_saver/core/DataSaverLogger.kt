package com.funny.data_saver.core

class DataSaverLogger(private val tag: String) {
    private fun output(level: String, msg: String) {
        println("$level/$tag: $msg")
    }

    fun d(msg: String) {
        if (DataSaverConfig.DEBUG) output("D", msg)
    }
    fun w(msg: String) {
        if (DataSaverConfig.DEBUG) output("W", msg)
    }
    fun e(msg: String) {
        if (DataSaverConfig.DEBUG) output("E", msg)
    }

    companion object {
        private const val TAG = "ComposeDataSaver"
        private val logger by lazy(LazyThreadSafetyMode.PUBLICATION) {
            DataSaverLogger(TAG)
        }

        fun log(msg: String) = d(msg)
        fun d(msg: String) { logger.d(msg) }
        fun w(msg: String) { logger.w(msg) }
        fun e(msg: String) { logger.e(msg) }
    }
}

package com.funny.aitoy.core.prefs

import com.funny.data_saver.core.DataSaverInterface
import com.funny.data_saver_mmkv.DefaultDataSaverMMKV

actual val DataSaverUtils: DataSaverInterface = DefaultDataSaverMMKV

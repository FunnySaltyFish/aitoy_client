package com.funny.submaker.network

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DynamicTimeout(
    val connectTimeout: Int = -1,
    val readTimeout: Int = -1,
    val writeTimeout: Int = -1,
    val timeoutParamExtractor: KClass<out TimeoutParamExtractor> = DefaultTimeoutParamExtractor::class,
)

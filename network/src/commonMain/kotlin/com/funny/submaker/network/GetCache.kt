package com.funny.submaker.network

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class GetCache(
    val maxAgeSeconds: Int = 30,
    val staleIfErrorSeconds: Int = 86_400,
    val refreshAt4Am: Boolean = false,
    val minSeconds: Int = 60,
)

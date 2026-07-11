# Retrofit 依赖运行时注解和泛型签名解析接口方法，release 混淆时必须保留这些属性。
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep interface com.funny.aitoy.network.api.service.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization 生成的序列化器由运行时按约定查找，保留可避免 release 包下诊断请求体解析异常。
-keepclassmembers class com.funny.aitoy.network.api.service.** {
    public static ** Companion;
    public static kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.funny.aitoy.network.api.service.**$$serializer { *; }

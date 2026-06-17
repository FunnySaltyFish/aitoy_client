# openai-java 的 schema 生成依赖会引用桌面 JDK 才有的反射类型。
# Android 运行时不会走到这些类型，release 混淆时忽略即可。
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

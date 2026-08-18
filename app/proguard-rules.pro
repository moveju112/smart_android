# kotlinx.serialization — @Serializable 클래스의 이름과 직렬화기를 지운다면
# 매크로 파일을 읽고 쓸 수 없게 된다
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wemade.smartnoti.**$$serializer { *; }
-keepclassmembers class com.wemade.smartnoti.** {
    *** Companion;
}
-keepclasseswithmembers class com.wemade.smartnoti.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 매니페스트에서 이름으로 불리는 것들
-keep class com.wemade.smartnoti.MacroService { *; }
-keep class com.wemade.smartnoti.InstallResultReceiver { *; }

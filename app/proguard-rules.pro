# Regras do R8 para o build de release.
# Room, WorkManager, OkHttp e Compose já trazem as próprias regras.

# Mantém nomes de enums gravados no banco (Room usa o nome em texto).
-keepclassmembers enum io.github.valeriofra.mova.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Workers são instanciados por reflexão pelo WorkManager.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Mecanismo de extração (youtubedl-android + Jackson usam reflexão).
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**

# O yt-dlp é extraído com o commons-compress, que cria classes por reflexão
# (ExtraFieldUtils → AsiExtraField, X5455_ExtendedTimestamp...). Sem estas
# regras o R8 remove os construtores e o app fecha ao preparar o mecanismo.
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**

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

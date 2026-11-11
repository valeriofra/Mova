package io.github.valeriofra.mova

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o motivo de um fechamento inesperado do app, para que o usuário possa
 * compartilhar o relatório na próxima abertura. Nada é enviado automaticamente.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_LENGTH = 30_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Relatório da última falha, se houver. */
    fun pending(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun save(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("MOVA ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Data: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
        file(context).writeText(report.take(MAX_LENGTH))
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}

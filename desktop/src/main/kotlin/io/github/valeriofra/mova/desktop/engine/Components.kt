package io.github.valeriofra.mova.desktop.engine

import io.github.valeriofra.mova.desktop.system.OsInfo
import java.io.File

/** Peças do mecanismo de download, baixadas no primeiro uso. */
enum class ComponentId { YT_DLP, FFMPEG, JS_RUNTIME }

/** Como o arquivo baixado é distribuído. */
enum class Packaging { EXECUTABLE, ZIP, TAR_XZ }

/**
 * De onde baixar um componente e como instalá-lo em `engine/`.
 *
 * Sempre de fontes oficiais no GitHub: yt-dlp (compilação nightly, a que recebe
 * primeiro as correções), FFmpeg do próprio projeto yt-dlp (com os ajustes que
 * ele recomenda) e QuickJS-ng (interpretador JavaScript pequeno, usado pelo
 * yt-dlp para resolver os desafios do YouTube).
 */
data class ComponentSpec(
    val id: ComponentId,
    val url: String,
    /** Arquivo "hash  nome" publicado junto com o componente, quando existe. */
    val checksumsUrl: String?,
    val packaging: Packaging,
    /** Pasta (dentro de `engine/`) onde o componente fica. */
    val installDir: String,
    /** Para executáveis soltos: nome final do arquivo. */
    val fileName: String? = null,
    /** Para pacotes: arquivos a manter (pelo nome, sem pastas). */
    val keep: (String) -> Boolean = { true },
    /** Tamanho aproximado do download, para a barra de progresso. */
    val approxBytes: Long,
) {
    val assetName: String get() = url.substringAfterLast('/')
}

object Components {

    private const val YT_DLP = "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download"
    private const val FFMPEG = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest"
    private const val QJS = "https://github.com/quickjs-ng/quickjs/releases/latest/download"

    /** Componentes necessários neste sistema. O FFmpeg do sistema (Linux) dispensa o download. */
    fun specsFor(os: OsInfo): List<ComponentSpec> {
        val arm = os.arch == OsInfo.Arch.ARM64
        return when (os.family) {
            // No Windows ARM, os executáveis x64 rodam por emulação (o próprio app é x64).
            OsInfo.Family.WINDOWS -> listOf(
                ComponentSpec(
                    ComponentId.YT_DLP, "$YT_DLP/yt-dlp.exe", "$YT_DLP/SHA2-256SUMS",
                    Packaging.EXECUTABLE, installDir = "", fileName = "yt-dlp.exe", approxBytes = 24_000_000,
                ),
                ComponentSpec(
                    ComponentId.FFMPEG, "$FFMPEG/ffmpeg-master-latest-win64-gpl-shared.zip", "$FFMPEG/checksums.sha256",
                    Packaging.ZIP, installDir = "ffmpeg",
                    keep = { name -> name == "ffmpeg.exe" || name == "ffprobe.exe" || name.endsWith(".dll") },
                    approxBytes = 91_000_000,
                ),
                ComponentSpec(
                    ComponentId.JS_RUNTIME, "$QJS/qjs-windows-x86_64.exe", null,
                    Packaging.EXECUTABLE, installDir = "", fileName = "qjs.exe", approxBytes = 1_900_000,
                ),
            )
            else -> listOf(
                ComponentSpec(
                    ComponentId.YT_DLP, "$YT_DLP/${if (arm) "yt-dlp_linux_aarch64" else "yt-dlp_linux"}", "$YT_DLP/SHA2-256SUMS",
                    Packaging.EXECUTABLE, installDir = "", fileName = "yt-dlp", approxBytes = 41_000_000,
                ),
                ComponentSpec(
                    ComponentId.FFMPEG,
                    "$FFMPEG/ffmpeg-master-latest-${if (arm) "linuxarm64" else "linux64"}-gpl.tar.xz",
                    "$FFMPEG/checksums.sha256",
                    Packaging.TAR_XZ, installDir = "ffmpeg",
                    keep = { name -> name == "ffmpeg" || name == "ffprobe" },
                    approxBytes = 158_000_000,
                ),
                ComponentSpec(
                    ComponentId.JS_RUNTIME, "$QJS/${if (arm) "qjs-linux-aarch64" else "qjs-linux-x86_64"}", null,
                    Packaging.EXECUTABLE, installDir = "", fileName = "qjs", approxBytes = 2_600_000,
                ),
            )
        }
    }

    /** Caminhos dos executáveis instalados em [engineDir]. */
    data class Layout(val engineDir: File, val os: OsInfo) {
        val ytDlp: File get() = File(engineDir, "yt-dlp${os.executableSuffix}")
        val ffmpegDir: File get() = File(engineDir, "ffmpeg")
        val ffmpeg: File get() = File(ffmpegDir, "ffmpeg${os.executableSuffix}")
        val jsRuntime: File get() = File(engineDir, "qjs${os.executableSuffix}")

        fun mainFile(id: ComponentId): File = when (id) {
            ComponentId.YT_DLP -> ytDlp
            ComponentId.FFMPEG -> ffmpeg
            ComponentId.JS_RUNTIME -> jsRuntime
        }
    }

    /** Lê "hash  nome" (formato do sha256sum) e devolve o hash de [assetName]. */
    fun checksumFor(sums: String, assetName: String): String? =
        sums.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) parts[1].removePrefix("*") to parts[0].lowercase() else null
            }
            .firstOrNull { (name, _) -> name == assetName }
            ?.second
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

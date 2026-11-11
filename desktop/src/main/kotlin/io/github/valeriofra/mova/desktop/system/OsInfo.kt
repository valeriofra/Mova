package io.github.valeriofra.mova.desktop.system

import java.util.Locale

/** Sistema operacional e arquitetura em que o MOVA está rodando. */
data class OsInfo(val family: Family, val arch: Arch) {
    enum class Family { WINDOWS, LINUX, MAC, OTHER }
    enum class Arch { X64, ARM64, OTHER }

    val isWindows: Boolean get() = family == Family.WINDOWS
    val executableSuffix: String get() = if (isWindows) ".exe" else ""

    companion object {
        val current: OsInfo by lazy { detect(System.getProperty("os.name"), System.getProperty("os.arch")) }

        fun detect(osName: String?, osArch: String?): OsInfo {
            val name = osName.orEmpty().lowercase(Locale.ROOT)
            val family = when {
                name.startsWith("windows") -> Family.WINDOWS
                name.contains("linux") -> Family.LINUX
                name.contains("mac") || name.contains("darwin") -> Family.MAC
                else -> Family.OTHER
            }
            val arch = when (osArch.orEmpty().lowercase(Locale.ROOT)) {
                "amd64", "x86_64", "x64" -> Arch.X64
                "aarch64", "arm64" -> Arch.ARM64
                else -> Arch.OTHER
            }
            return OsInfo(family, arch)
        }
    }
}

package io.github.valeriofra.mova.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Consulta rápida do estado da rede. */
class NetworkMonitor(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun isConnected(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Rede tarifada (dados móveis, roteador do celular...). */
    fun isMetered(): Boolean = connectivity.isActiveNetworkMetered
}

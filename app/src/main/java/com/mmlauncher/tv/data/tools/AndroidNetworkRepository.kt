package com.mmlauncher.tv.data.tools

import com.mmlauncher.tv.domain.model.NetInterface
import com.mmlauncher.tv.domain.model.NetKind
import com.mmlauncher.tv.domain.model.NetworkSnapshot
import com.mmlauncher.tv.domain.repository.NetworkRepository
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** IP addresses of the box and whether ADB over network (port 5555) is listening. */
class AndroidNetworkRepository : NetworkRepository {

    override fun snapshot(): NetworkSnapshot {
        val list = try { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val interfaces = list.filter { it.isUp && !it.isLoopback }.mapNotNull { nif ->
            val ip = nif.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
            if (ip == null) return@mapNotNull null
            NetInterface(nif.name, kindOf(nif.name), ip, macOf(nif))
        }.sortedBy { it.kind.ordinal }
        return NetworkSnapshot(interfaces, adbListening())
    }

    private fun kindOf(name: String) = when {
        name.startsWith("wlan") -> NetKind.WIFI
        name.startsWith("eth") -> NetKind.ETHERNET
        else -> NetKind.OTHER
    }

    /** Android 11+ hides the MAC from apps; /sys sometimes still shows it (the real one, useful for routers). */
    private fun macOf(nif: NetworkInterface): String? {
        nif.hardwareAddress?.takeIf { it.isNotEmpty() }?.let { b -> return b.joinToString(":") { "%02X".format(it) } }
        return try {
            File("/sys/class/net/${nif.name}/address").readText().trim().uppercase().takeIf { it.length == 17 && it != "02:00:00:00:00:00" }
        } catch (e: Exception) { null }
    }

    /** A bare TCP connect: nothing is sent, so no approval dialog is triggered. */
    private fun adbListening(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 5555), 400) }
        true
    } catch (e: Exception) {
        false
    }
}

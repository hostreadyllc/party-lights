package com.ash.partylights

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Keeps several phones on the same Wi-Fi (or hotspot) in step.
 * The host broadcasts its settings, show phase and beat count; guests follow.
 */
class PartySync(context: Context) {
    data class Remote(
        val settings: PartySettings,
        val phase: Double,
        val beats: Int,
        val receivedAt: Long,
    )

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val deviceId = UUID.randomUUID().toString().take(8)
    private val sender = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null
    private var receiver: Thread? = null
    private var lock: WifiManager.MulticastLock? = null
    @Volatile private var running = false

    @Volatile var role = SyncRole.OFF
        private set

    /** Latest state received from a host (guest side). */
    @Volatile var remote: Remote? = null
        private set

    private val guestsSeen = HashMap<String, Long>()

    /** Number of guests seen in the last few seconds (host side). */
    val guestCount: Int
        get() = synchronized(guestsSeen) {
            val now = System.nanoTime()
            guestsSeen.values.count { now - it < 6_000_000_000L }
        }

    val hostConnected: Boolean
        get() = remote?.let { System.nanoTime() - it.receivedAt < 3_000_000_000L } ?: false

    fun start(newRole: SyncRole) {
        if (newRole == role && (running || newRole == SyncRole.OFF)) return
        stop()
        role = newRole
        if (newRole == SyncRole.OFF) return
        try {
            lock = wifi.createMulticastLock("party-sync").apply { setReferenceCounted(false); acquire() }
            val s = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000
                bind(InetSocketAddress(PORT))
            }
            socket = s
            running = true
            receiver = Thread({ receiveLoop(s) }, "party-sync").apply { isDaemon = true; start() }
        } catch (_: Exception) {
            running = false
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        receiver = null
        try { lock?.release() } catch (_: Exception) {}
        lock = null
        remote = null
        synchronized(guestsSeen) { guestsSeen.clear() }
        role = SyncRole.OFF
    }

    fun broadcastState(settings: PartySettings, phase: Double, beats: Int) {
        if (role != SyncRole.HOST) return
        val msg = JSONObject()
            .put("t", "state").put("id", deviceId)
            .put("s", settings.toJson()).put("p", phase).put("b", beats)
        send(msg.toString())
    }

    fun sendHello() {
        if (role != SyncRole.GUEST) return
        send(JSONObject().put("t", "hello").put("id", deviceId).toString())
    }

    private fun send(text: String) {
        val s = socket ?: return
        sender.execute {
            val data = text.toByteArray()
            for (addr in broadcastAddresses()) {
                try { s.send(DatagramPacket(data, data.size, addr, PORT)) } catch (_: Exception) {}
            }
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val list = mutableListOf(InetAddress.getByName("255.255.255.255"))
        try {
            @Suppress("DEPRECATION")
            val dhcp = wifi.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val bc = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                val bytes = ByteArray(4) { k -> (bc shr (k * 8) and 0xFF).toByte() }
                list += InetAddress.getByAddress(bytes)
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(8192)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                val o = JSONObject(String(packet.data, 0, packet.length))
                val id = o.optString("id")
                if (id == deviceId) continue
                when (o.optString("t")) {
                    "state" -> if (role == SyncRole.GUEST) {
                        remote = Remote(
                            PartySettings.fromJson(o.getJSONObject("s")),
                            o.optDouble("p", 0.0),
                            o.optInt("b", 0),
                            System.nanoTime(),
                        )
                    }
                    "hello" -> if (role == SyncRole.HOST) {
                        synchronized(guestsSeen) { guestsSeen[id] = System.nanoTime() }
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    companion object {
        const val PORT = 47474
    }
}

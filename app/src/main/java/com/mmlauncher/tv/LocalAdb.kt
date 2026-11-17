package com.mmlauncher.tv

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Minimal ADB client that talks to the box's own adbd on 127.0.0.1:5555.
 *
 * Why: Android does not let an ordinary app read other apps' memory/storage numbers (`dumpsys`),
 * nor grant itself permissions. The shell user can. If "ADB over network" (port 5555) is on, the
 * launcher can ask adbd to run a command as shell. The user approves the launcher's key once with
 * the usual "Allow USB debugging?" dialog on the TV. Nothing leaves the device.
 */
object LocalAdb {

    private const val HOST = "127.0.0.1"
    private const val PORT = 5555
    private const val A_CNXN = 0x4e584e43
    private const val A_AUTH = 0x48545541
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_CLSE = 0x45534c43
    private const val A_WRTE = 0x45545257
    private const val VERSION = 0x01000001
    private const val MAX_DATA = 256 * 1024
    private const val MAX_OUTPUT = 6 * 1024 * 1024

    /** DigestInfo prefix for SHA-1: adb "signs" the 20-byte token as if it were a SHA-1 digest. */
    private val SHA1_PREFIX = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    const val ENABLE_HINT = "adb tcpip 5555"

    private class Packet(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Last failure, shown by the UI: "refused" (adbd not on TCP), "denied" (user said no) or "error". */
    @Volatile var lastError: String? = null

    /**
     * Runs one shell command through the local adbd and returns its output, or null on failure.
     * Blocking: may wait up to [authWaitMs] for the user to approve the key on the TV (first time).
     */
    fun shell(context: Context, command: String, authWaitMs: Int = 30000, runMs: Int = 30000): String? {
        lastError = null
        return try {
            val keys = loadKeys(context)
            Socket().use { sock ->
                try {
                    sock.connect(InetSocketAddress(HOST, PORT), 1500)
                } catch (e: Exception) {
                    lastError = "refused"
                    return null
                }
                sock.soTimeout = authWaitMs
                val out = sock.getOutputStream()
                val inp = DataInputStream(sock.getInputStream())

                send(out, A_CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
                var signed = false
                while (true) {
                    val p = read(inp)
                    when {
                        p.cmd == A_CNXN -> break
                        p.cmd == A_AUTH && p.arg0 == 1 -> {
                            if (!signed) {
                                signed = true
                                send(out, A_AUTH, 2, 0, sign(keys, p.data))
                            } else {
                                // Key not known yet: offer it; the TV shows "Allow USB debugging?".
                                send(out, A_AUTH, 3, 0, publicKeyBytes(keys))
                            }
                        }
                        else -> { lastError = "error"; return null }
                    }
                }

                sock.soTimeout = runMs
                send(out, A_OPEN, 1, 0, "shell:$command\u0000".toByteArray())
                val result = ByteArrayOutputStream()
                var remote = 0
                while (true) {
                    val p = read(inp)
                    when (p.cmd) {
                        A_OKAY -> remote = p.arg0
                        A_WRTE -> {
                            remote = p.arg0
                            if (result.size() < MAX_OUTPUT) result.write(p.data)
                            send(out, A_OKAY, 1, remote)
                        }
                        A_CLSE -> break
                        else -> { lastError = "error"; return null }
                    }
                }
                try { send(out, A_CLSE, 1, remote) } catch (_: Exception) {}
                String(result.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: java.net.SocketTimeoutException) {
            lastError = "denied"
            null
        } catch (e: Exception) {
            if (lastError == null) lastError = "error"
            null
        }
    }

    // ---- protocol -------------------------------------------------------------------------

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xff
        return sum
    }

    private fun send(out: OutputStream, cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size).putInt(checksum(data)).putInt(cmd.inv())
        out.write(h.array())
        if (data.isNotEmpty()) out.write(data)
        out.flush()
    }

    private fun read(inp: DataInputStream): Packet {
        val h = ByteArray(24)
        inp.readFully(h)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int; val a0 = b.int; val a1 = b.int; val len = b.int
        b.int; val magic = b.int
        require(magic == cmd.inv() && len in 0..MAX_DATA) { "bad packet" }
        val data = ByteArray(len)
        if (len > 0) inp.readFully(data)
        return Packet(cmd, a0, a1, data)
    }

    // ---- keys -----------------------------------------------------------------------------

    private fun loadKeys(context: Context): KeyPair {
        val priv = File(context.filesDir, "adbkey")
        val pub = File(context.filesDir, "adbkey.pub")
        if (priv.exists() && pub.exists()) {
            try {
                val kf = KeyFactory.getInstance("RSA")
                return KeyPair(
                    kf.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())),
                )
            } catch (_: Exception) { /* regenerate below */ }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        return kp
    }

    private fun sign(keys: KeyPair, token: ByteArray): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(keys.private)
            update(SHA1_PREFIX + token)
            sign()
        }

    /** adb's own public key format: base64 of {len, n0inv, n, rr, e} followed by a label and NUL. */
    private fun publicKeyBytes(keys: KeyPair): ByteArray =
        (Base64.encodeToString(encodePublicKey(keys.public as RSAPublicKey), Base64.NO_WRAP) + " mmlauncher@tv\u0000")
            .toByteArray()

    fun encodePublicKey(pub: RSAPublicKey): ByteArray {
        val n = pub.modulus
        val words = 64 // 2048 bits / 32
        val two32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = two32.subtract(n.mod(two32).modInverse(two32)).toLong().toInt() // -n^-1 mod 2^32
        val rr = BigInteger.ONE.shiftLeft(4096).mod(n) // (2^2048)^2 mod n
        val bb = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(words).putInt(n0inv).put(littleEndian(n, 256)).put(littleEndian(rr, 256))
        bb.putInt(pub.publicExponent.toInt())
        return bb.array()
    }

    private fun littleEndian(v: BigInteger, size: Int): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(size)
        for (i in 0 until minOf(be.size, size)) out[i] = be[be.size - 1 - i]
        return out
    }
}

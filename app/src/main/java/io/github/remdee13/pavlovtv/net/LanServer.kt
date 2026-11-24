package io.github.remdee13.pavlovtv.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * Tiny web server for the local network, used to send things from a phone to the TV
 * (photos, news links). Typing on a TV remote is slow; a phone keyboard is not.
 *
 * The phone opens http://<tv-ip>:<port>/<token>. The random token keeps other people on the
 * same Wi-Fi out. A server only runs while the matching TV screen is open.
 *
 * [handle] receives the path after the token: "" for the page itself, "upload" for
 * "/<token>/upload", and so on.
 */
class LanServer(
    private val maxBody: Int,
    private val handle: suspend (Request) -> Response,
) {
    class Request(val method: String, val path: String, val body: ByteArray)

    class Response(val code: Int, val type: String, val body: ByteArray) {
        companion object {
            fun html(html: String) = Response(200, "text/html; charset=utf-8", html.toByteArray())
            fun json(json: String) = Response(200, "application/json; charset=utf-8", json.toByteArray())
            fun text(code: Int, text: String) = Response(code, "text/plain; charset=utf-8", text.toByteArray())
        }
    }

    val token: String = buildString {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
    private var socket: ServerSocket? = null
    private var job: Job? = null

    /** Starts listening and returns the address to show as a QR code, or null without a network. */
    fun start(scope: CoroutineScope): String? {
        val ip = localIpAddress() ?: return null
        val server = ServerSocket().apply {
            reuseAddress = true
            runCatching { bind(InetSocketAddress(PREFERRED_PORT)) }.onFailure { bind(InetSocketAddress(0)) }
        }
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { runCatching { client.use { serve(it) } } }
            }
        }
        return "http://$ip:${server.localPort}/$token"
    }

    fun stop() {
        runCatching { socket?.close() }
        job?.cancel()
        socket = null
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val requestLine = readLine(input) ?: return
        var length = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("content-length", ignoreCase = true)) {
                length = line.substring(colon + 1).trim().toIntOrNull() ?: 0
            }
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val fullPath = parts[1].substringBefore('?')
        val path = when {
            fullPath == "/$token" -> ""
            fullPath.startsWith("/$token/") -> fullPath.removePrefix("/$token/")
            else -> return respond(out, Response.text(404, "not found"))
        }
        if (length < 0 || length > maxBody) return respond(out, Response.text(413, "too large"))
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        if (read < length) return respond(out, Response.text(400, "incomplete"))
        respond(out, handle(Request(parts[0], path, body)))
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (b == '\n'.code) return buffer.toString().trimEnd('\r')
            buffer.append(b.toChar())
            if (buffer.length > 8192) return null
        }
    }

    private fun respond(out: OutputStream, response: Response) {
        val status = when (response.code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 413 -> "Payload Too Large"; else -> "Error"
        }
        out.write(
            ("HTTP/1.1 ${response.code} $status\r\nContent-Type: ${response.type}\r\n" +
                "Content-Length: ${response.body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray(),
        )
        out.write(response.body)
        out.flush()
    }

    companion object {
        private const val PREFERRED_PORT = 8765

        /** Escapes text for HTML. */
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        /** Escapes text for a single-quoted JavaScript string inside a script tag. */
        fun js(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c").replace("\n", " ")

        /** Shared look of the phone pages: the launcher's glass card on its aurora colors. */
        const val PAGE_STYLE = """
body{margin:0;min-height:100vh;font-family:system-ui,-apple-system,sans-serif;color:#fff;background:linear-gradient(135deg,#0b1f3a,#3b2a7a 60%,#7c4dff);display:flex;align-items:center;justify-content:center}
.card{box-sizing:border-box;width:calc(100% - 48px);margin:24px;padding:28px;max-width:440px;border-radius:28px;background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.3);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);box-shadow:0 20px 60px rgba(0,0,0,.35)}
h1{margin:0 0 8px;font-size:24px}p{opacity:.8;line-height:1.4}
label,button{box-sizing:border-box;display:block;width:100%;cursor:pointer;margin-top:16px;padding:16px;border:0;border-radius:999px;text-align:center;font:600 16px system-ui,-apple-system,sans-serif;background:rgba(255,255,255,.9);color:#1b1b3a}
input[type=text]{box-sizing:border-box;width:100%;margin-top:16px;padding:15px 18px;border-radius:16px;border:1px solid rgba(255,255,255,.4);background:rgba(0,0,0,.25);color:#fff;font-size:16px;outline:none}
#s{margin-top:16px;min-height:1.4em;opacity:.9}
ul{padding-left:20px;margin:12px 0 0;opacity:.9}li{margin:4px 0}
"""

        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}

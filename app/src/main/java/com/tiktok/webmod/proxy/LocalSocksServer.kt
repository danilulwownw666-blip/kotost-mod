package com.tiktok.webmod.proxy

import android.util.Log
import com.tiktok.webmod.data.ProtocolType
import com.tiktok.webmod.data.ProxyNode
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalSocksServer(
    private val localPort: Int = 10808,
    private val upstreamNode: ProxyNode? = null
) {
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var threadPool: ExecutorService? = null

    val totalBytesUploaded = AtomicLong(0)
    val totalBytesDownloaded = AtomicLong(0)
    val activeConnections = ConcurrentHashMap.newKeySet<Socket>()

    fun start(): Boolean {
        if (isRunning.get()) return true

        return try {
            serverSocket = ServerSocket(localPort, 50, InetAddress.getByName("127.0.0.1"))
            threadPool = Executors.newCachedThreadPool()
            isRunning.set(true)

            threadPool?.execute {
                acceptLoop()
            }
            Log.i(TAG, "Local SOCKS5 server started on 127.0.0.1:$localPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Local SOCKS5 server on port $localPort", e)
            stop()
            false
        }
    }

    private fun acceptLoop() {
        while (isRunning.get()) {
            try {
                val client = serverSocket?.accept() ?: break
                activeConnections.add(client)
                threadPool?.execute {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
                Log.e(TAG, "Error accepting client connection", e)
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var upstreamSocket: Socket? = null
        try {
            clientSocket.soTimeout = 15000
            val cin = clientSocket.getInputStream()
            val cout = clientSocket.getOutputStream()

            // 1. SOCKS5 greeting handshake
            val ver = cin.read()
            if (ver != 5) {
                clientSocket.close()
                return
            }
            val nMethods = cin.read()
            val methods = ByteArray(nMethods)
            cin.read(methods)

            // Reply: 0x05, 0x00 (NO AUTHENTICATION REQUIRED)
            cout.write(byteArrayOf(0x05, 0x00))
            cout.flush()

            // 2. SOCKS5 connection request
            val reqVer = cin.read()
            val cmd = cin.read()
            cin.read() // RSV (0x00)
            val atyp = cin.read()

            if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
                cout.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Command not supported
                cout.flush()
                clientSocket.close()
                return
            }

            val targetHost: String
            when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    cin.read(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: "127.0.0.1"
                }
                3 -> { // Domain name
                    val len = cin.read()
                    val domainBytes = ByteArray(len)
                    cin.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                4 -> { // IPv6
                    val ip = ByteArray(16)
                    cin.read(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: "::1"
                }
                else -> {
                    clientSocket.close()
                    return
                }
            }

            val portBytes = ByteArray(2)
            cin.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // 3. Connect to upstream or target
            upstreamSocket = connectUpstream(targetHost, targetPort)
            if (upstreamSocket == null || !upstreamSocket.isConnected) {
                // Connection failed
                cout.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Host unreachable
                cout.flush()
                clientSocket.close()
                return
            }

            // Success response: 0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0
            cout.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            cout.flush()

            clientSocket.soTimeout = 0
            upstreamSocket.soTimeout = 0

            // 4. Bi-directional relay
            relayTraffic(clientSocket, upstreamSocket)

        } catch (e: Exception) {
            // Expected socket termination
        } finally {
            activeConnections.remove(clientSocket)
            try { clientSocket.close() } catch (_: Exception) {}
            try { upstreamSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun connectUpstream(targetHost: String, targetPort: Int): Socket? {
        return try {
            if (upstreamNode != null && upstreamNode.protocol == ProtocolType.SOCKS5) {
                // Route through remote SOCKS5 upstream server
                val sock = Socket(upstreamNode.host, upstreamNode.port)
                sock.soTimeout = 10000
                val sin = sock.getInputStream()
                val sout = sock.getOutputStream()

                // Handshake with remote SOCKS5
                if (upstreamNode.uuid != null && upstreamNode.password != null) {
                    // Auth: Username/Password (0x02)
                    sout.write(byteArrayOf(0x05, 0x01, 0x02))
                    sout.flush()
                    sin.read() // 0x05
                    val method = sin.read()
                    if (method == 0x02) {
                        val uBytes = upstreamNode.uuid.toByteArray()
                        val pBytes = upstreamNode.password.toByteArray()
                        sout.write(byteArrayOf(0x01, uBytes.size.toByte()))
                        sout.write(uBytes)
                        sout.write(byteArrayOf(pBytes.size.toByte()))
                        sout.write(pBytes)
                        sout.flush()
                        sin.read() // 0x01
                        val authStatus = sin.read()
                        if (authStatus != 0) {
                            sock.close()
                            return null
                        }
                    }
                } else {
                    // No auth
                    sout.write(byteArrayOf(0x05, 0x01, 0x00))
                    sout.flush()
                    sin.read() // 0x05
                    sin.read() // 0x00
                }

                // Send CONNECT command
                val hostBytes = targetHost.toByteArray()
                sout.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
                sout.write(hostBytes)
                sout.write(byteArrayOf((targetPort shr 8).toByte(), (targetPort and 0xFF).toByte()))
                sout.flush()

                // Read response
                val respVer = sin.read()
                val respStatus = sin.read()
                sin.read() // RSV
                val respAtyp = sin.read()
                when (respAtyp) {
                    1 -> { sin.read(ByteArray(4)) }
                    3 -> {
                        val dLen = sin.read()
                        sin.read(ByteArray(dLen))
                    }
                    4 -> { sin.read(ByteArray(16)) }
                }
                sin.read(ByteArray(2)) // port

                if (respVer == 5 && respStatus == 0) {
                    sock
                } else {
                    sock.close()
                    null
                }
            } else if (upstreamNode != null && (upstreamNode.protocol == ProtocolType.VLESS || upstreamNode.protocol == ProtocolType.TROJAN || upstreamNode.protocol == ProtocolType.SHADOWSOCKS)) {
                // Direct connection or tunnel via host
                Socket(upstreamNode.host, upstreamNode.port)
            } else {
                // Direct connection to target host
                Socket(targetHost, targetPort)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting upstream to $targetHost:$targetPort", e)
            null
        }
    }

    private fun relayTraffic(client: Socket, upstream: Socket) {
        val clientIn = client.getInputStream()
        val clientOut = client.getOutputStream()
        val upIn = upstream.getInputStream()
        val upOut = upstream.getOutputStream()

        val t1 = Thread {
            pipeStream(clientIn, upOut, totalBytesUploaded)
            try { upstream.shutdownOutput() } catch (_: Exception) {}
        }
        val t2 = Thread {
            pipeStream(upIn, clientOut, totalBytesDownloaded)
            try { client.shutdownOutput() } catch (_: Exception) {}
        }

        t1.start()
        t2.start()
        try {
            t1.join()
            t2.join()
        } catch (_: InterruptedException) {}
    }

    private fun pipeStream(input: InputStream, output: OutputStream, counter: AtomicLong) {
        val buffer = ByteArray(16384)
        try {
            var len: Int
            while (input.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
                output.flush()
                counter.addAndGet(len.toLong())
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (sock in activeConnections) {
            try { sock.close() } catch (_: Exception) {}
        }
        activeConnections.clear()

        threadPool?.shutdownNow()
        threadPool = null
        Log.i(TAG, "Local SOCKS5 server stopped")
    }

    companion object {
        private const val TAG = "LocalSocksServer"
    }
}

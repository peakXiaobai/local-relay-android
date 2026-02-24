package com.peak.localrelay

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSecure
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.handle
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.readRemaining
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RelayEngine {
    private var server: ApplicationEngine? = null
    private var client: HttpClient? = null

    suspend fun start(config: RelayConfig, log: (String) -> Unit) {
        stop()

        val baseUrl = Url(config.targetBaseUrl)
        val currentClient = HttpClient(ClientCIO) {
            expectSuccess = false
            install(ClientWebSockets)
        }

        val host = if (config.bindAllInterfaces) "0.0.0.0" else "127.0.0.1"
        val currentServer = embeddedServer(ServerCIO, host = host, port = config.localPort) {
            install(ServerWebSockets)

            routing {
                webSocket("{...}") {
                    proxyWebSocket(call, this, currentClient, baseUrl, log)
                }

                route("{...}") {
                    handle {
                        proxyHttp(call, currentClient, baseUrl, log)
                    }
                }
            }
        }

        currentServer.start(wait = false)

        client = currentClient
        server = currentServer
        log("Relay started: http://$host:${config.localPort} -> ${baseUrl.protocol.name}://${baseUrl.host}:${baseUrl.port}")
    }

    suspend fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
        client?.close()
        client = null
    }

    private suspend fun proxyHttp(
        call: ApplicationCall,
        client: HttpClient,
        baseUrl: Url,
        log: (String) -> Unit,
    ) {
        val targetUrl = buildTargetUrl(baseUrl, call.request.path(), call.request.queryParameters)

        try {
            val requestBody = call.receiveChannel().readRemaining().readBytes()
            val response = client.request(targetUrl) {
                method = call.request.httpMethod
                copyRequestHeaders(call.request.headers, headers)
                if (requestBody.isNotEmpty() && allowsRequestBody(call.request.httpMethod)) {
                    setBody(requestBody)
                }
            }

            copyResponseHeaders(response.headers) { key, value ->
                call.response.headers.append(key, value)
            }
            val responseBytes = response.bodyAsChannel().readRemaining().readBytes()

            call.respondBytes(
                bytes = responseBytes,
                contentType = response.headers["Content-Type"]?.let { io.ktor.http.ContentType.parse(it) },
                status = response.status,
            )

            log("HTTP ${call.request.httpMethod.value} ${call.request.uri} -> ${response.status.value}")
        } catch (error: Throwable) {
            log("HTTP proxy error: ${error.message}")
            call.respond(HttpStatusCode.BadGateway, "Relay error: ${error.message}")
        }
    }

    private suspend fun proxyWebSocket(
        call: ApplicationCall,
        downstream: DefaultWebSocketSession,
        client: HttpClient,
        baseUrl: Url,
        log: (String) -> Unit,
    ) {
        val targetUrl = buildTargetUrl(baseUrl, call.request.path(), call.request.queryParameters)
        val wsUrl = URLBuilder(targetUrl).apply {
            protocol = if (baseUrl.protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
        }.buildString()

        try {
            val upstream = client.webSocketSession {
                url(wsUrl)
                copyRequestHeaders(call.request.headers, headers)
            }

            if (!upstream.isActive) {
                downstream.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Upstream WebSocket unavailable"))
                return
            }

            log("WebSocket connected: ${call.request.uri}")

            coroutineScope {
                val downstreamToUpstream = async {
                    pipeFrames(downstream, upstream)
                }
                val upstreamToDownstream = async {
                    pipeFrames(upstream, downstream)
                }

                awaitAll(downstreamToUpstream, upstreamToDownstream)
            }

            upstream.close()
            log("WebSocket closed: ${call.request.uri}")
        } catch (error: Throwable) {
            log("WebSocket proxy error: ${error.message}")
            downstream.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Relay WebSocket error"))
        }
    }

    private suspend fun pipeFrames(source: WebSocketSession, destination: WebSocketSession) {
        try {
            for (frame in source.incoming) {
                when (frame) {
                    is Frame.Text -> destination.send(Frame.Text(frame.readText()))
                    is Frame.Binary -> destination.send(Frame.Binary(frame.fin, frame.readBytes()))
                    is Frame.Ping -> destination.send(Frame.Ping(frame.readBytes()))
                    is Frame.Pong -> destination.send(Frame.Pong(frame.readBytes()))
                    is Frame.Close -> {
                        destination.close(frame.readReason() ?: CloseReason(CloseReason.Codes.NORMAL, "closed"))
                        return
                    }
                }
            }
        } catch (_: CancellationException) {
            // ignored
        }
    }

    private fun allowsRequestBody(method: HttpMethod): Boolean {
        return method != HttpMethod.Get && method != HttpMethod.Head && method != HttpMethod.Options
    }

    private fun buildTargetUrl(baseUrl: Url, incomingPath: String, incomingQuery: Parameters): Url {
        val mergedPath = mergePath(baseUrl.encodedPath, incomingPath)

        return URLBuilder(baseUrl).apply {
            encodedPath = mergedPath
            parameters.clear()
            incomingQuery.forEach { key, values ->
                values.forEach { value ->
                    parameters.append(key, value)
                }
            }
        }.build()
    }

    private fun mergePath(basePath: String, incomingPath: String): String {
        val normalizedBase = basePath.trim().trimEnd('/')
        val normalizedIncoming = incomingPath.trim().trimStart('/')

        return when {
            normalizedBase.isEmpty() && normalizedIncoming.isEmpty() -> "/"
            normalizedBase.isEmpty() -> "/$normalizedIncoming"
            normalizedIncoming.isEmpty() -> if (normalizedBase.startsWith('/')) normalizedBase else "/$normalizedBase"
            else -> {
                val base = if (normalizedBase.startsWith('/')) normalizedBase else "/$normalizedBase"
                "$base/$normalizedIncoming"
            }
        }
    }

    private fun copyRequestHeaders(source: Headers, target: HeadersBuilder) {
        val skip = setOf(
            "host",
            "connection",
            "proxy-connection",
            "upgrade",
            "keep-alive",
            "transfer-encoding",
            "content-length",
        )

        source.forEach { key, values ->
            if (key.lowercase() in skip) {
                return@forEach
            }
            values.forEach { value ->
                target.append(key, value)
            }
        }
    }

    private fun copyResponseHeaders(source: Headers, append: (String, String) -> Unit) {
        val skip = setOf(
            "connection",
            "transfer-encoding",
            "content-length",
        )

        source.forEach { key, values ->
            if (key.lowercase() in skip) {
                return@forEach
            }
            values.forEach { value ->
                append(key, value)
            }
        }
    }
}

package com.armsx2.remoteplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Transporte de dados multiplayer do Ultimate Rage.
 * Não altera o Remote Play existente.
 */
object UltimateRageOnline {
    private const val TAG = "UltimateRageOnline"
    private const val CHANNEL = "ultimate-rage-game"
    private const val VERSION = 1

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var rtc: RemotePlayRtc.Factory? = null
    private var peer: PeerConnection? = null
    private var channel: DataChannel? = null
    private var lobby: RemotePlayLobby.Lobby? = null
    private var pollTask: ScheduledFuture<*>? = null
    private var gatherWait: RemotePlayRtc.GatherWait? = null

    private var onMessage: ((JSONObject) -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null
    private var closed = true

    @Synchronized
    fun createRoom(
        context: Context,
        status: (String) -> Unit,
        message: (JSONObject) -> Unit
    ) {
        if (!closed) return

        closed = false
        onStatus = status
        onMessage = message
        status("Preparando conexão...")

        executor.execute {
            try {
                val factory = RemotePlayRtc.createFactory(context.applicationContext)
                rtc = factory

                val pc = factory.factory.createPeerConnection(
                    RemotePlayRtc.configuration(),
                    observer()
                ) ?: throw IllegalStateException("Não foi possível criar a conexão")

                peer = pc

                val wait = RemotePlayRtc.GatherWait(main, 8_000) {
                    val sdp = pc.localDescription?.description
                        ?: return@GatherWait fail("Descrição local não encontrada")

                    executor.execute {
                        publishRoom(sdp)
                    }
                }

                gatherWait = wait
                createDataChannel(pc)

                pc.createOffer(
                    object : SdpAdapter(::fail) {
                        override fun onCreateSuccess(description: SessionDescription) {
                            pc.setLocalDescription(
                                object : SdpAdapter(::fail) {
                                    override fun onSetSuccess() {
                                        wait.localDescriptionSet()
                                    }
                                },
                                description
                            )
                        }
                    },
                    org.webrtc.MediaConstraints()
                )
            } catch (error: Exception) {
                fail(error.message ?: "Erro ao criar sala")
            }
        }
    }

    private fun publishRoom(sdp: String) {
        try {
            val created = RemotePlayLobby.open(sdp)
                ?: throw IllegalStateException("Não foi possível criar a sala")

            lobby = created
            status("Sala criada: ${RemotePlayLobby.display(created.code)}")

            pollTask = executor.scheduleWithFixedDelay({
                try {
                    when (val result = RemotePlayLobby.collectAnswer(created)) {
                        is RemotePlayLobby.Result.Ok -> {
                            pollTask?.cancel(false)
                            pollTask = null

                            // Result.Ok.value já contém o SDP puro.
                            val answer = result.value

                            peer?.setRemoteDescription(
                                object : SdpAdapter(::fail) {
                                    override fun onSetSuccess() {
                                        status("Resposta recebida. Conectando...")
                                    }
                                },
                                SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    answer
                                )
                            )
                        }

                        RemotePlayLobby.Result.NotFound ->
                            fail("Sala não encontrada")

                        RemotePlayLobby.Result.Busy ->
                            fail("A sala já está ocupada")

                        RemotePlayLobby.Result.Unreachable ->
                            status("Aguardando resposta...")

                        RemotePlayLobby.Result.Waiting -> Unit
                    }
                } catch (error: Exception) {
                    fail(error.message ?: "Erro ao consultar sala")
                }
            }, 1, 2, TimeUnit.SECONDS)
        } catch (error: Exception) {
            fail(error.message ?: "Erro ao publicar sala")
        }
    }

    @Synchronized
    fun joinRoom(
        context: Context,
        code: String,
        status: (String) -> Unit,
        message: (JSONObject) -> Unit
    ) {
        if (!closed) return

        val normalized = RemotePlayLobby.normalize(code)
            ?: run {
                status("Código de sala inválido")
                return
            }

        closed = false
        onStatus = status
        onMessage = message
        status("Procurando sala...")

        executor.execute {
            try {
                val offerResult = RemotePlayLobby.offer(normalized)

                if (offerResult !is RemotePlayLobby.Result.Ok) {
                    throw IllegalStateException("Sala não encontrada ou indisponível")
                }

                // Result.Ok.value já contém o SDP puro.
                val offer = offerResult.value

                val factory = RemotePlayRtc.createFactory(context.applicationContext)
                rtc = factory

                val pc = factory.factory.createPeerConnection(
                    RemotePlayRtc.configuration(),
                    observer()
                ) ?: throw IllegalStateException("Não foi possível criar a conexão")

                peer = pc

                val wait = RemotePlayRtc.GatherWait(main, 8_000) {
                    val answer = pc.localDescription?.description
                        ?: return@GatherWait fail("Resposta local não encontrada")

                    executor.execute {
                        sendAnswer(normalized, answer)
                    }
                }

                gatherWait = wait

                pc.setRemoteDescription(
                    object : SdpAdapter(::fail) {
                        override fun onSetSuccess() {
                            pc.createAnswer(
                                object : SdpAdapter(::fail) {
                                    override fun onCreateSuccess(description: SessionDescription) {
                                        pc.setLocalDescription(
                                            object : SdpAdapter(::fail) {
                                                override fun onSetSuccess() {
                                                    wait.localDescriptionSet()
                                                }
                                            },
                                            description
                                        )
                                    }
                                },
                                org.webrtc.MediaConstraints()
                            )
                        }
                    },
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        offer
                    )
                )
            } catch (error: Exception) {
                fail(error.message ?: "Erro ao entrar na sala")
            }
        }
    }

    private fun sendAnswer(code: String, sdp: String) {
        try {
            val result = RemotePlayLobby.answer(code, sdp)

            if (result !is RemotePlayLobby.Result.Ok) {
                throw IllegalStateException("Não foi possível enviar a resposta")
            }

            status("Resposta enviada. Conectando...")
        } catch (error: Exception) {
            fail(error.message ?: "Erro ao responder sala")
        }
    }

    private fun createDataChannel(pc: PeerConnection) {
        val init = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }

        val data = pc.createDataChannel(CHANNEL, init)
            ?: throw IllegalStateException("Não foi possível criar canal de dados")

        channel = data
        observeChannel(data)
    }

    private fun observer() = object : PeerObserverAdapter() {
        override fun onDataChannel(data: DataChannel) {
            if (data.label() == CHANNEL) {
                channel = data
                observeChannel(data)
            }
        }

        override fun onIceGatheringChange(
            state: PeerConnection.IceGatheringState?
        ) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                gatherWait?.gatheringComplete()
            }
        }

        override fun onConnectionChange(
            state: PeerConnection.PeerConnectionState
        ) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED ->
                    status("Jogadores conectados!")

                PeerConnection.PeerConnectionState.FAILED ->
                    fail("Conexão falhou. A rede pode não permitir conexão direta.")

                PeerConnection.PeerConnectionState.DISCONNECTED ->
                    status("Conexão interrompida")

                else -> Unit
            }
        }
    }

    private fun observeChannel(data: DataChannel) {
        data.registerObserver(MessageObserver { buffer ->
            try {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)

                val json = JSONObject(String(bytes, Charsets.UTF_8))

                main.post {
                    onMessage?.invoke(json)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Pacote inválido", error)
            }
        })
    }

    fun sendPosition(
        x: Float,
        y: Float,
        z: Float,
        rotation: Float,
        animation: Int
    ) {
        val packet = JSONObject()
            .put("v", VERSION)
            .put("type", "player")
            .put("x", x)
            .put("y", y)
            .put("z", z)
            .put("rotation", rotation)
            .put("animation", animation)

        send(packet)
    }

    fun send(packet: JSONObject) {
        val data = channel ?: return
        if (data.state() != DataChannel.State.OPEN) return

        val bytes = packet.toString().toByteArray(Charsets.UTF_8)
        data.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    private fun status(value: String) {
        main.post {
            onStatus?.invoke(value)
        }
    }

    private fun fail(reason: String) {
        Log.e(TAG, reason)
        status("Erro: $reason")
    }

    @Synchronized
    fun close() {
        closed = true

        pollTask?.cancel(true)
        pollTask = null

        gatherWait?.cancel()
        gatherWait = null

        runCatching { channel?.close() }
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        runCatching { rtc?.dispose() }

        lobby?.let { room ->
            executor.execute {
                runCatching { RemotePlayLobby.close(room) }
            }
        }

        channel = null
        peer = null
        rtc = null
        lobby = null
        onMessage = null
        onStatus = null
    }
}

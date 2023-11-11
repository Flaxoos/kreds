/*
 *  Copyright (C) 2022 Abhijith Shivaswamy
 *   See the notice.md file distributed with this work for additional
 *   information regarding copyright ownership.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.github.crackthecodeabhi.kreds.connection

import io.github.crackthecodeabhi.kreds.messages.InlineCommandRedisMessage
import io.github.crackthecodeabhi.kreds.messages.RedisMessage
import io.github.crackthecodeabhi.kreds.messages.RedisMessageType
import io.github.crackthecodeabhi.kreds.messages.withReentrantLock
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.util.pipeline.Pipeline
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import mu.KotlinLogging
import kotlinx.coroutines.channels.Channel as KChannel

/**
 * A [KonnectionImpl] manages the state and interactions of a TCP connection
 * for communicating with a remote Redis server, adapted for Kotlin Multiplatform projects.
 *
 * The critical sections that access and/or modify the state are protected with a [Mutex] to ensure
 * thread-safe (or in this case, coroutine-safe) access to shared resources.
 *
 * A [Konnection]'s state is dependent on the following components:
 * 1. [Socket]: A handle to the underlying TCP socket.
 * 2. [ByteReadChannel]: A channel that enables reading data from the socket.
 * 3. [ByteWriteChannel]: A channel that enables writing data to the socket.
 *
 * When a connection is established (via [connect()]), the [Socket] and [ByteReadChannel]/[ByteWriteChannel] are configured.
 *
 * The class operates under the following assumptions:
 * - If [isConnected()] returns true, the state variables ([socket], [readChannel], and [writeChannel]) are non-null and operational.
 * - Any I/O error will result in the connection being closed and resources being cleaned up.
 */

private val logger = KotlinLogging.logger {}

internal abstract class KonnectionImpl(
    internal open val endpoint: Endpoint,
    val config: KredsClientConfig.() -> Unit
) : Konnection {

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: Socket? = null
    private var connection: Connection? = null
    private var readChannel: KChannel<RedisMessage>? = null
    private val readPipeline = Pipeline<RedisMessage, ReadPipelineContext>(Encoding, Timeout)
    private val writePipeline =
        Pipeline<RedisMessage, WritePipelineContext>(
            Decoding,
            BulkStringAggregation,
            ArrayAggregation,
            Response,
            Timeout
        ).apply {
            intercept(Decoding) {

            }
            intercept(BulkStringAggregation) {

            }
            intercept(ArrayAggregation) {

            }
            intercept(Response) {

            }
            intercept(Timeout) {

            }
        }

    //TODO: Ensure proper serialization/deserialization of RedisMessage
    private val messageChannel: KChannel<RedisMessage> = KChannel(Channel.UNLIMITED)

    override suspend fun isConnected(): Boolean = withReentrantLock {
        socket?.let { !it.isClosed } == true
    }

    override suspend fun flush(): Unit = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        else {
            connection!!.input.cancel()
            connection!!.output.close()
        }
    }

    override suspend fun write(message: RedisMessage): Unit = writeInternal(message, false)

    override suspend fun writeAndFlush(message: RedisMessage): Unit = writeInternal(message, true)


    override suspend fun read(): RedisMessage = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        return@withReentrantLock messageChannel.receive() // or handle the exception accordingly
    }

    override suspend fun connect(): Unit = withReentrantLock {
        if (!isConnected()) {
            val serverSocket = aSocket(selectorManager).tcp().bind(InetSocketAddress(endpoint.host, endpoint.port))
            socket = serverSocket.accept()
            val receiveChannel = socket!!.openReadChannel()
            connection = socket!!.connection()

            receiveChannel.readMessages()
            logger.trace { "New connection created to $endpoint" }
        }
    }


    private suspend fun writeInternal(message: RedisMessage, flush: Boolean): Unit = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        with(connection?.output ?: throw KredsConnectionException("Write channel is not available.")) {
            val context = WritePipelineContext(this)
            writePipeline.execute(context = context, subject = message)
            if (flush) {
                connection?.output?.flush()
            }
        }
    }

    private suspend fun ByteReadChannel.readMessages() = withContext(Dispatchers.IO) {

        while (isConnected()) {
            try {
                val messageType = RedisMessageType.readFrom(this@readMessages.readByte(), TODO())
                when (messageType) {
                    RedisMessageType.INLINE_COMMAND -> InlineCommandRedisMessage(Buffer()this@readMessages.readRemaining().also {   })
                    RedisMessageType.SIMPLE_STRING -> TODO()
                    RedisMessageType.ERROR -> TODO()
                    RedisMessageType.INTEGER -> TODO()
                    RedisMessageType.BULK_STRING -> TODO()
                    RedisMessageType.ARRAY_HEADER -> TODO()
                }
                with(connection?.input ?: throw KredsConnectionException("Read channel is not available.")) {
                    val context = ReadPipelineContext(this)
                    readPipeline.execute(context = context, subject = readChannel!!.receive())
                }
            } catch (e: Exception) {
                // TODO: Handle exceptions such as closed channels, etc.
            }
        }
    }

    override suspend fun disconnect(): Unit = withReentrantLock {
        connection?.output?.close()
        connection?.input?.cancel()
        socket?.close()
        socket = null
        connection = null
        messageChannel.close()
    }
}

public fun String.toRedisMessage(): RedisMessage = TODO()

public data class WritePipelineContext(val data: ByteWriteChannel)
public data class ReadPipelineContext(val data: ByteReadChannel)

internal val Encoding = PipelinePhase("Encoding")

internal val Decoding = PipelinePhase("Decoding")
internal val BulkStringAggregation = PipelinePhase("BulkStringAggregation")
internal val ArrayAggregation = PipelinePhase("ArrayAggregation")
internal val Timeout = PipelinePhase("Timeout")
internal val Response = PipelinePhase("Response")

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

import io.github.crackthecodeabhi.kreds.redis.RedisMessage
import io.github.crackthecodeabhi.kreds.withReentrantLock
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import mu.KotlinLogging

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
    val config: KredsClientConfig
) : Konnection {

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    // Note: Ensure proper serialization/deserialization of RedisMessage
    private val messageChannel: Channel<RedisMessage> = Channel(Channel.UNLIMITED)

    override suspend fun isConnected(): Boolean = withReentrantLock {
        socket?.let { !it.isClosed } == true
    }

    override suspend fun flush(): Unit = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        else writeChannel!!.flush()
    }

    override suspend fun write(message: RedisMessage): Unit = writeInternal(message, false)

    override suspend fun writeAndFlush(message: RedisMessage): Unit = writeInternal(message, true)


    override suspend fun read(): RedisMessage = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        return@withReentrantLock messageChannel.receive() // or handle the exception accordingly
    }

    override suspend fun connect(): Unit = withReentrantLock {
        if (!isConnected()) {
            socket = aSocket(selectorManager).tcp().connect(InetSocketAddress(endpoint.host, endpoint.port))
            readChannel = socket!!.openReadChannel()
            writeChannel = socket!!.openWriteChannel(autoFlush = false)

            readMessages()
            logger.trace { "New connection created to $endpoint" }
        }
    }


    private suspend fun writeInternal(message: RedisMessage, flush: Boolean): Unit = withReentrantLock {
        if (!isConnected()) throw KredsNotYetConnectedException()
        writeChannel?.apply {
            // TODO: Ensure you're writing data according to the protocol
            writeStringUtf8("your serialized message here")
            if (flush) {
                flush()
            }
        } ?: throw KredsConnectionException("Write channel is not available.")
    }

    private suspend fun readMessages() = withContext(Dispatchers.IO) {
        while (isConnected()) {
            try {
                // Note: Ensure you're reading data according to the protocol
                val message = readChannel?.readUTF8Line()

                if (message != null) {
                    // TODO: Convert `message` to `RedisMessage` and send it to `messageChannel`
                    messageChannel.send(message.toRedisMessage())
                }
            } catch (e: Exception) {
                // TODO: Handle exceptions such as closed channels, etc.
            }
        }
    }

    override suspend fun disconnect(): Unit = withReentrantLock {
        socket?.close()
        socket = null
        readChannel = null
        writeChannel = null
        messageChannel.close()
    }
}

public fun String.toRedisMessage(): RedisMessage = TODO()
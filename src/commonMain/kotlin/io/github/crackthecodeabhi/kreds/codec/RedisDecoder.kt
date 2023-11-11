/*
 *  Copyright (C) 2023 Ido Flax
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

package io.github.crackthecodeabhi.kreds.codec

import io.github.crackthecodeabhi.kreds.exceptions.CodecException
import io.github.crackthecodeabhi.kreds.messages.ArrayHeaderRedisMessage
import io.github.crackthecodeabhi.kreds.messages.BulkStringHeaderRedisMessage
import io.github.crackthecodeabhi.kreds.messages.DefaultBulkStringRedisContent
import io.github.crackthecodeabhi.kreds.messages.DefaultLastBulkStringRedisContent
import io.github.crackthecodeabhi.kreds.messages.ErrorRedisMessage
import io.github.crackthecodeabhi.kreds.messages.FullBulkStringRedisMessage
import io.github.crackthecodeabhi.kreds.messages.InlineCommandRedisMessage
import io.github.crackthecodeabhi.kreds.messages.IntegerRedisMessage
import io.github.crackthecodeabhi.kreds.messages.RedisConstants
import io.github.crackthecodeabhi.kreds.messages.RedisMessage
import io.github.crackthecodeabhi.kreds.messages.RedisMessageType
import io.github.crackthecodeabhi.kreds.messages.SimpleStringRedisMessage
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import kotlinx.io.Buffer
import kotlinx.io.files.FileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlin.jvm.JvmOverloads
import kotlin.math.min


/**
 * Decodes the Redis protocol into [RedisMessage] objects following
 * [RESP (REdis Serialization Protocol)](https://redis.io/topics/protocol).
 *
 * [RedisMessage] parts can be aggregated to [RedisMessage] using
 * [RedisArrayAggregator] or processed directly.
 */
class RedisDecoder @JvmOverloads constructor(
    maxInlineMessageLength: Int,
    messagePool: RedisMessagePool,
    decodeInlineCommands: Boolean = false
) :
    ByteToMessageDecoder() {
    private val toPositiveLongProcessor = ToPositiveLongProcessor()
    private val decodeInlineCommands: Boolean
    private val maxInlineMessageLength: Int
    private val messagePool: RedisMessagePool

    // current decoding states
    private var state = State.DECODE_TYPE
    private var type: RedisMessageType? = null
    private var remainingBulkLength = 0

    private enum class State {
        DECODE_TYPE,
        DECODE_INLINE,

        // SIMPLE_STRING, ERROR, INTEGER
        DECODE_LENGTH,

        // BULK_STRING, ARRAY_HEADER
        DECODE_BULK_STRING_EOL,
        DECODE_BULK_STRING_CONTENT
    }
    /**
     * Creates a new instance with default `maxInlineMessageLength` and `messagePool`.
     * @param decodeInlineCommands if `true`, inline commands will be decoded.
     */
    /**
     * Creates a new instance with default `maxInlineMessageLength` and `messagePool`
     * and inline command decoding disabled.
     */
    @JvmOverloads
    constructor(decodeInlineCommands: Boolean = false) : this(
        RedisConstants.REDIS_INLINE_MESSAGE_MAX_LENGTH,
        FixedRedisMessagePool.INSTANCE,
        decodeInlineCommands
    )
    /**
     * Creates a new instance.
     * @param maxInlineMessageLength the maximum length of inline message.
     * @param messagePool the predefined message pool.
     * @param decodeInlineCommands if `true`, inline commands will be decoded.
     */
    /**
     * Creates a new instance with inline command decoding disabled.
     * @param maxInlineMessageLength the maximum length of inline message.
     * @param messagePool the predefined message pool.
     */
    init {
        if (maxInlineMessageLength <= 0 || maxInlineMessageLength > RedisConstants.REDIS_MESSAGE_MAX_LENGTH) {
            throw CodecException(
                "maxInlineMessageLength: " + maxInlineMessageLength +
                        " (expected: <= " + RedisConstants.REDIS_MESSAGE_MAX_LENGTH + ")"
            )
        }
        this.maxInlineMessageLength = maxInlineMessageLength
        this.messagePool = messagePool
        this.decodeInlineCommands = decodeInlineCommands
    }

    @Throws(java.lang.Exception::class)
    protected override fun decode(ctx: ChannelHandlerContext, `in`: Buffer, out: MutableList<Any>) {
        try {
            while (true) {
                when (state) {
                    State.DECODE_TYPE -> if (!decodeType(`in`)) {
                        return
                    }

                    State.DECODE_INLINE -> if (!decodeInline(`in`, out)) {
                        return
                    }

                    State.DECODE_LENGTH -> if (!decodeLength(`in`, out)) {
                        return
                    }

                    State.DECODE_BULK_STRING_EOL -> if (!decodeBulkStringEndOfLine(`in`, out)) {
                        return
                    }

                    State.DECODE_BULK_STRING_CONTENT -> if (!decodeBulkStringContent(`in`, out)) {
                        return
                    }

                    else -> throw CodecException("Unknown state: $state")
                }
            }
        } catch (e: CodecException) {
            resetDecoder()
            throw e
        } catch (e: java.lang.Exception) {
            resetDecoder()
            throw CodecException(e)
        }
    }

    private fun resetDecoder() {
        state = State.DECODE_TYPE
        remainingBulkLength = 0
    }

    @Throws(java.lang.Exception::class)
    private fun decodeType(`in`: Buffer): Boolean {
        if (!`in`.isReadable()) {
            return false
        }
        type = RedisMessageType.readFrom(`in`, decodeInlineCommands)
        state = if (type.isInline()) State.DECODE_INLINE else State.DECODE_LENGTH
        return true
    }

    @Throws(java.lang.Exception::class)
    private fun decodeInline(`in`: Buffer, out: MutableList<Any>): Boolean {
        val lineBytes: Buffer? = readLine(`in`)
        if (lineBytes == null) {
            if (`in`.readableBytes() > maxInlineMessageLength) {
                throw CodecException(
                    ("length: " + `in`.readableBytes() +
                            " (expected: <= " + maxInlineMessageLength + ")")
                )
            }
            return false
        }
        out.add(newInlineRedisMessage(type, lineBytes))
        resetDecoder()
        return true
    }

    @Throws(java.lang.Exception::class)
    private fun decodeLength(`in`: Buffer, out: MutableList<Any>): Boolean {
        val lineBuffer: Buffer? = readLine(`in`) ?: return false
        val length = parseRedisNumber(lineBuffer)
        if (length < RedisConstants.NULL_VALUE) {
            throw CodecException("length: " + length + " (expected: >= " + RedisConstants.NULL_VALUE + ")")
        }
        when (type) {
            RedisMessageType.ARRAY_HEADER -> {
                out.add(ArrayHeaderRedisMessage(length))
                resetDecoder()
                return true
            }

            RedisMessageType.BULK_STRING -> {
                if (length > RedisConstants.REDIS_MESSAGE_MAX_LENGTH) {
                    throw CodecException(
                        ("length: " + length + " (expected: <= " +
                                RedisConstants.REDIS_MESSAGE_MAX_LENGTH + ")")
                    )
                }
                remainingBulkLength = length.toInt() // range(int) is already checked.
                return decodeBulkString(`in`, out)
            }

            else -> throw CodecException("bad type: $type")
        }
    }

    @Throws(java.lang.Exception::class)
    private fun decodeBulkString(`in`: Buffer, out: MutableList<Any>): Boolean {
        when (remainingBulkLength) {
            RedisConstants.NULL_VALUE -> {
                out.add(FullBulkStringRedisMessage.NULL_INSTANCE)
                resetDecoder()
                return true
            }

            0 -> {
                state = State.DECODE_BULK_STRING_EOL
                return decodeBulkStringEndOfLine(`in`, out)
            }

            else -> {
                out.add(BulkStringHeaderRedisMessage(remainingBulkLength))
                state = State.DECODE_BULK_STRING_CONTENT
                return decodeBulkStringContent(`in`, out)
            }
        }
    }

    // $0\r\n <here> \r\n
    @Throws(java.lang.Exception::class)
    private fun decodeBulkStringEndOfLine(`in`: Buffer, out: MutableList<Any>): Boolean {
        if (`in`.readableBytes() < RedisConstants.EOL_LENGTH) {
            return false
        }
        readEndOfLine(`in`)
        out.add(FullBulkStringRedisMessage.EMPTY_INSTANCE)
        resetDecoder()
        return true
    }

    // ${expectedBulkLength}\r\n <here> {data...}\r\n
    @Throws(java.lang.Exception::class)
    private fun decodeBulkStringContent(`in`: Buffer, out: MutableList<Any>): Boolean {
        val readableBytes: Int = `in`.readableBytes()
        if (readableBytes == 0 || remainingBulkLength == 0 && readableBytes < RedisConstants.EOL_LENGTH) {
            return false
        }

        // if this is last frame.
        if (readableBytes >= remainingBulkLength + RedisConstants.EOL_LENGTH) {
            val content: Buffer = `in`.readSlice(remainingBulkLength)
            readEndOfLine(`in`)
            // Only call retain after readEndOfLine(...) as the method may throw an exception.
            out.add(DefaultLastBulkStringRedisContent(content.retain()))
            resetDecoder()
            return true
        }

        // chunked write.
        val toRead = min(remainingBulkLength.toDouble(), readableBytes.toDouble()).toInt()
        remainingBulkLength -= toRead
        out.add(DefaultBulkStringRedisContent(`in`.readSlice(toRead).retain()))
        return true
    }

    private fun newInlineRedisMessage(
        messageType: RedisMessageType,
        content: Buffer
    ): RedisMessage {
        when (messageType) {
            RedisMessageType.INLINE_COMMAND -> return InlineCommandRedisMessage(
                content.toString()
            )

            RedisMessageType.SIMPLE_STRING -> {
                val cached: SimpleStringRedisMessage = messagePool.getSimpleString(content)
                return if (cached != null) cached else SimpleStringRedisMessage(
                    content.toString(

                    )
                )
            }

            RedisMessageType.ERROR -> {
                val cached: ErrorRedisMessage = messagePool.getError(content)
                return if (cached != null) cached else ErrorRedisMessage(
                    content.toString(

                    )
                )
            }

            RedisMessageType.INTEGER -> {
                val cached: IntegerRedisMessage = messagePool.getInteger(content)
                return if (cached != null) cached else IntegerRedisMessage(
                    parseRedisNumber(
                        content
                    )
                )
            }

            else -> throw CodecException("bad type: $messageType")
        }
    }

    private fun parseRedisNumber(buffer: Buffer): Long {
        val readableBytes: Long = buffer.size
        val negative = readableBytes > 0 && buffer[readableBytes - 1] == '-'.code.toByte()
        val extraOneByteForNegative = if (negative) 1 else 0
        if (readableBytes <= extraOneByteForNegative) {
            throw CodecException("no number to parse: ${buffer.readString()}")
        }
        if (readableBytes > RedisConstants.POSITIVE_LONG_MAX_LENGTH + extraOneByteForNegative) {
            throw CodecException(
                "too many characters to be a valid RESP Integer: ${buffer.readString()}"
            )
        }
        return if (negative) {
            -parsePositiveNumber(buffer.apply { skip(extraOneByteForNegative.toLong()) })
        } else parsePositiveNumber(buffer)
    }

    private fun parsePositiveNumber(buffer: Buffer): Long {
        toPositiveLongProcessor.reset()
        buffer.readByteArray(buffer.size.toInt()).map { toPositiveLongProcessor.process(it) }
        return toPositiveLongProcessor.content()
    }

    private class ToPositiveLongProcessor() {
        private var result: Long = 0

        fun process(value: Byte): Boolean {
            if (value < '0'.code.toByte() || value > '9'.code.toByte()) {
                throw CodecException("bad byte in number: $value")
            }
            result = result * 10 + (value - '0'.code.toByte())
            return true
        }

        fun content(): Long {
            return result
        }

        fun reset() {
            result = 0
        }
    }

    companion object {
        private fun readEndOfLine(`in`: Buffer) {
            val delim: Short = `in`.readShort()
            if (RedisConstants.EOL_SHORT == delim) {
                return
            }
            val bytes: ByteArray = RedisCodecUtil.shortToBytes(delim)
            throw CodecException("delimiter: [" + bytes[0] + "," + bytes[1] + "] (expected: \\r\\n)")
        }

        private fun readLine(`in`: Buffer): Buffer? {
            if (!`in`.isReadable(RedisConstants.EOL_LENGTH)) {
                return null
            }
            val lfIndex: Int = `in`.forEachByte(ByteProcessor.FIND_LF)
            if (lfIndex < 0) {
                return null
            }
            val data: Buffer = `in`.readSlice(lfIndex - `in`.readerIndex() - 1) // `-1` is for CR
            readEndOfLine(`in`) // validate CR LF
            return data
        }
    }
}


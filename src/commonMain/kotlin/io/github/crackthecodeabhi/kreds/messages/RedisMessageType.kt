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

package io.github.crackthecodeabhi.kreds.messages

import io.github.crackthecodeabhi.kreds.exceptions.CodecException
import kotlinx.io.Buffer


internal enum class RedisMessageType private constructor (
    private val value: Byte?,
    /**
     * Returns `true` if this type is inline type, or returns `false`. If this is `true`,
     * this type doesn't have length field.
     */
    val isInline: Boolean
) {
    INLINE_COMMAND(null, true),
    SIMPLE_STRING('+'.code.toByte(), true),
    ERROR('-'.code.toByte(), true),
    INTEGER(':'.code.toByte(), true),
    BULK_STRING('$'.code.toByte(), false),
    ARRAY_HEADER('*'.code.toByte(), false);

    /**
     * Returns length of this type.
     */
    fun length(): Int {
        return if (value != null) RedisConstants.TYPE_LENGTH else 0
    }

    /**
     * Write the message type's prefix to the given buffer.
     */
    fun writeTo(out: Buffer) {
        if (value == null) {
            return
        }
        out.writeByte(value)
    }

     companion object {
        /**
         * Determine [RedisMessageType] based on the type prefix `byte` read from given the buffer.
         */
        fun readFrom(`in`: Buffer, decodeInlineCommands: Boolean): RedisMessageType {
            val byte = `in`[0]
            return readFrom(byte, decodeInlineCommands)
        }

        /**
         * Determine [RedisMessageType] based on the type prefix `byte`
         */
         fun readFrom(
            byte: Byte,
            decodeInlineCommands: Boolean
        ): RedisMessageType {
            val type = valueOf(byte)
            if (type == INLINE_COMMAND) {
                if (!decodeInlineCommands) {
                    throw CodecException("Decoding of inline commands is disabled")
                }
            }
            return type
        }

        private fun valueOf(value: Byte): RedisMessageType {
            return when (value) {
                '+'.code.toByte() -> SIMPLE_STRING
                '-'.code.toByte() -> ERROR
                ':'.code.toByte() -> INTEGER
                '$'.code.toByte() -> BULK_STRING
                '*'.code.toByte() -> ARRAY_HEADER
                else -> INLINE_COMMAND
            }
        }
    }
}

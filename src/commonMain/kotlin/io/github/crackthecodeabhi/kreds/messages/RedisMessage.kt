/*
 *  Copyright (C) 2023 Abhijith Shivaswamy
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

internal const val ANONYMOUS = "Anonymous"
internal val Any.classSimpleName: String
    get() = this::class.simpleName ?: ANONYMOUS

internal sealed interface RedisMessage

/**
 * Constant values for Redis encoder/decoder.
 */
internal object RedisConstants {
    const val TYPE_LENGTH = 1
    const val EOL_LENGTH = 2
    const val NULL_LENGTH = 2
    const val NULL_VALUE = -1
    const val REDIS_MESSAGE_MAX_LENGTH = 512 * 1024 * 1024 // 512MB

    // 64KB is max inline length of current Redis server implementation.
    const val REDIS_INLINE_MESSAGE_MAX_LENGTH = 64 * 1024
    const val POSITIVE_LONG_MAX_LENGTH = 19 // length of Long.MAX_VALUE
    const val LONG_MAX_LENGTH = POSITIVE_LONG_MAX_LENGTH + 1 // +1 is sign
}

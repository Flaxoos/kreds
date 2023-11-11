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

import io.github.crackthecodeabhi.kreds.io.DefaultBufferHolder
import kotlinx.io.Buffer

/**
 * An aggregated bulk string of [RESP](https://redis.io/topics/protocol)
 *
 * @constructor  Creates a [FullBulkStringRedisMessage] for the given {@code content}.
 * @param content the content, must not be {@code null}. If content is null or empty,
 * use {@link FullBulkStringRedisMessage#NULL_INSTANCE} or {@link FullBulkStringRedisMessage#EMPTY_INSTANCE}
 * instead of constructor.
 */
internal open class FullBulkStringRedisMessage(content: Buffer) : RedisMessage, DefaultBufferHolder(content) {

    private constructor() : this(Buffer())

    /**
     * Returns whether the content of this message is `null`.
     *
     * @return indicates whether the content of this message is `null`.
     */
    fun isNull(): Boolean {
        return false
    }

    override fun toString(): String = "${classSimpleName}[content=$content]"

    /**
     * A predefined null instance of [FullBulkStringRedisMessage].
     */
    object NullInstance : FullBulkStringRedisMessage(Buffer()) {
        val isNull: Boolean
            get() = true

        override val content: Buffer = Buffer()
    }

    /**
     * A predefined empty instance of [FullBulkStringRedisMessage].
     */
    object EmptyInstance : FullBulkStringRedisMessage() {
        override val content: Buffer = Buffer()
    }

    override fun copy(): FullBulkStringRedisMessage {
        return super.copy() as FullBulkStringRedisMessage
    }

    override fun duplicate(): FullBulkStringRedisMessage {
        return super.duplicate() as FullBulkStringRedisMessage
    }
}

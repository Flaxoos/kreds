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

package io.github.crackthecodeabhi.kreds.redis

import io.github.crackthecodeabhi.kreds.messages.RedisMessage
import io.github.crackthecodeabhi.kreds.messages.toRedisString

public actual sealed interface RedisMessage

public actual class AbstractStringRedisMessage actual constructor(public actual val content: String ) : RedisMessage {
    override fun toString(): String = toRedisString()
}
public actual class ArrayHeaderRedisMessage() : RedisMessage
public actual class ArrayRedisMessage() : RedisMessage
public actual class BulkStringHeaderRedisMessage() : RedisMessage
public actual class DefaultBulkStringRedisContent() : RedisMessage
public actual class DefaultLastBulkStringRedisContent() : RedisMessage
public actual class ErrorRedisMessage() : RedisMessage
public actual class FullBulkStringRedisMessage() : RedisMessage
public actual class InlineCommandRedisMessage() : RedisMessage
public actual class IntegerRedisMessage() : RedisMessage
public actual class SimpleStringRedisMessage() : RedisMessage

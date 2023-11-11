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

import io.github.crackthecodeabhi.kreds.io.BufferHolder
import kotlinx.io.Buffer

/**
 * A chunk of bulk strings which is used for Redis chunked transfer-encoding.
 * This content might be generated after a certain header message when the content is large or chunked.
 * If you prefer not to receive this content in your handler, ensure proper decoding or aggregation.
 */
internal interface BulkStringRedisContent : BufferHolder, RedisMessage {

    override val content: Buffer

    override fun copy(): BulkStringRedisContent

    override fun duplicate(): BulkStringRedisContent

    override fun replace(content: Buffer): BulkStringRedisContent

    // should and can we implement the other methods? retained and retainedDuplicate
}
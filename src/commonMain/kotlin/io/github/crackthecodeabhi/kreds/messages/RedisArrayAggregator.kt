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

import io.github.crackthecodeabhi.kreds.connection.WritePipelineContext
import io.github.crackthecodeabhi.kreds.exceptions.CodecException
import io.ktor.util.pipeline.PipelineInterceptor

internal object RedisArrayAggregator : RedisMessageInterceptor() {
    private val depths = ArrayDeque<AggregateState>()
    override val intercept: PipelineInterceptor<RedisMessage, WritePipelineContext> = {
        run {
            var msg: RedisMessage = it
            if (msg is ArrayHeaderRedisMessage) {
                if (decodeRedisArrayHeader(msg as ArrayHeaderRedisMessage) == null) {
                    return@run
                }
            } else {
                // TODO: implement if we need to implement retain 
                // ReferenceCountUtil.retain<RedisMessage>(msg)
            }
            while (!depths.isEmpty()) {
                val current: AggregateState = depths.last()
                current.children.add(msg)

                // if current aggregation completed, go to parent aggregation.
                if (current.children.size == current.size) {
                    msg = ArrayRedisMessage(current.children)
                    depths.last()
                } else {
                    return@run
                }
            }
            //TODO: is this the right function to call for doing netty out.add(msg)?
            this.proceedWith(msg)
        }
    }

    private fun decodeRedisArrayHeader(header: ArrayHeaderRedisMessage): ArrayRedisMessage? {
        return if (header.isNull()) {
            ArrayRedisMessage.NullInstance
        } else if (header.length == 0L) {
            ArrayRedisMessage.EmptyInstance
        } else if (header.length > 0L) {
            // Currently, this codec doesn't support `long` length for arrays because Java's List.size() is int.
            if (header.length > Int.MAX_VALUE) {
                throw CodecException("this codec doesn't support longer length than " + Int.MAX_VALUE)
            }

            // start aggregating array
            depths.add(AggregateState(header.length.toInt()))
            null
        } else {
            throw CodecException("bad length: " + header.length)
        }
    }

    private class AggregateState(val size: Int) {
        val children: MutableList<RedisMessage> = mutableListOf()
    }
}
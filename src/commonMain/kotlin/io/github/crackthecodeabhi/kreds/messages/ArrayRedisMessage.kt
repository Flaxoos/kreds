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

internal open class ArrayRedisMessage(internal val children: List<RedisMessage>) : RedisMessage {
    /**
     * Returns whether the content of this message is `null`.
     *
     * @return indicates whether the content of this message is `null`.
     */
    internal open fun isNull(): Boolean {
        return false
    }


    override fun toString(): String = "${classSimpleName}[children=${children.joinToString()}]"


    /**
     * A predefined null array instance for {@link ArrayRedisMessage}.
     */
    internal object NullInstance : ArrayRedisMessage(emptyList()) {
        override fun isNull(): Boolean {
            return true
        }

        override fun toString(): String {
            return "NullArrayRedisMessage"
        }
    }

    /**
     * A predefined null array instance for [ArrayRedisMessage].
     */
    internal object EmptyInstance : ArrayRedisMessage(emptyList()) {
        internal val isNull: Boolean
            get() = true

        override fun toString(): String {
            return "NullArrayRedisMessage"
        }
    }

}
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

package io.github.crackthecodeabhi.kreds.io

import kotlinx.io.Buffer

public open class DefaultBufferHolder(override val content: Buffer) : BufferHolder {

    private var position: Long = 0L

    override fun copy(): BufferHolder {
        return replace(content.copy())
    }


    //TODO: is this right? is it needed?
    override fun duplicate(): BufferHolder {
        val duplicate = DefaultBufferHolder(content)
        duplicate.position = this.position
        return duplicate
    }

    /**
     * {@inheritDoc}
     *
     *
     * Override this method to return a new instance of this object whose content is set to the specified
     * `content`. The default implementation of [.copy], [.duplicate] and
     * [.retainedDuplicate] invokes this method to create a copy.
     */
    override fun replace(content: Buffer): BufferHolder {
        return DefaultBufferHolder(content)
    }
}
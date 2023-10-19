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

package io.github.crackthecodeabhi.kreds

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * An exclusive object is to be accessed only while holding this mutex of that object.
 * Implementations should be @CoroutineSafe
 */
internal interface ExclusiveObject {
    val mutex: Mutex
    val key: ReentrantMutexContextKey
}

internal data class ReentrantMutexContextKey(val mutex: Mutex): CoroutineContext.Key<ReentrantMutexContextElement>
internal class ReentrantMutexContextElement(override val key: ReentrantMutexContextKey): CoroutineContext.Element

internal suspend inline fun <R> ExclusiveObject.withReentrantLock(crossinline block: suspend () -> R): R {
    if(coroutineContext[key] != null) return block()

    return withContext(ReentrantMutexContextElement(key)){
        this@withReentrantLock.mutex.withLock {
            block()
        }
    }
}
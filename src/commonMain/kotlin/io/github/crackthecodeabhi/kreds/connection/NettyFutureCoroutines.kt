package io.github.crackthecodeabhi.kreds.connection

import kotlinx.coroutines.Deferred
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal suspend fun <V> Deferred<V>.suspendableAwait(): V {
    return suspendCoroutine { cont ->
        invokeOnCompletion {
            if (it == null) { // No exception
                cont.resume(this.getCompleted())
            } else {
                cont.resumeWithException(it)
            }
        }
    }
}

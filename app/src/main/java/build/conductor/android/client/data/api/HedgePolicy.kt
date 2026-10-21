package build.conductor.android.client.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When to send another copy of a read that has not answered.
 * The live API answers about one request in six only after 2 to 8 seconds, at random.
 */
data class HedgePolicy(val delayMillis: Long, val maxCopies: Int) {
    companion object {
        val DEFAULT = HedgePolicy(delayMillis = 1_000, maxCopies = 3)
    }
}

/** Starts a new copy of [request] each [HedgePolicy.delayMillis] until one copy answers, then cancels the others. Use it only for reads. */
suspend fun <T> HedgePolicy.hedge(request: suspend () -> T): T = coroutineScope {
    val firstAnswer = CompletableDeferred<T>()
    val copies = List(maxCopies) { index ->
        launch {
            delay(delayMillis * index)
            firstAnswer.completeWith(answerOf(request))
        }
    }
    try {
        firstAnswer.await()
    } finally {
        copies.forEach(Job::cancel)
    }
}

private suspend fun <T> answerOf(request: suspend () -> T): Result<T> = try {
    Result.success(request())
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    Result.failure(exception)
}

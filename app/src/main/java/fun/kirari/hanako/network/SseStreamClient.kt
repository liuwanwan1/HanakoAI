package `fun`.kirari.hanako.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SseStreamClient(
    private val client: OkHttpClient
) {
    suspend fun stream(
        request: Request,
        onEvent: (eventSource: EventSource, type: String?, id: String?, data: String) -> String?,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val builder = StringBuilder()
            val finished = AtomicBoolean(false)

            fun finish(block: () -> Unit) {
                if (finished.compareAndSet(false, true)) {
                    block()
                }
            }

            val listener = object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    try {
                        val delta = onEvent(eventSource, type, id, data)
                        if (!delta.isNullOrEmpty()) {
                            builder.append(delta)
                            onDelta(delta)
                        }
                    } catch (t: Throwable) {
                        finish { cont.resumeWithException(t) }
                        eventSource.cancel()
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    finish {
                        cont.resumeWithException(
                            t ?: IllegalStateException(
                                "Stream failed: ${response?.code ?: "unknown"}"
                            )
                        )
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    finish { cont.resume(builder.toString()) }
                }
            }

            val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
            cont.invokeOnCancellation { eventSource.cancel() }
        }
    }
}

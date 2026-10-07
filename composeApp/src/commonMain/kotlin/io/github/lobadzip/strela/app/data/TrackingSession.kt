package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.model.TrackingView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrackingState(val view: TrackingView?, val error: String?, val connection: Connection)

/** The customer's live view of one order. Same pattern as the courier: REST first, then a socket. */
class TrackingSession(private val backend: () -> Backend, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(TrackingState(null, null, Connection.CONNECTING))
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private var job: Job? = null

    fun follow(code: String) {
        job?.cancel()
        _state.value = TrackingState(null, null, Connection.CONNECTING)
        job = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    _state.update { it.copy(view = backend().tracking(code), error = null) }
                    backend().trackingLive(code).collect { view ->
                        attempt = 0
                        _state.update { it.copy(view = view, error = null, connection = Connection.LIVE) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiFailure) {
                    if (e.status == 404) {
                        _state.update { it.copy(error = e.message) }
                        return@launch
                    }
                } catch (_: Throwable) {
                    // Reconnect below.
                }
                _state.update { it.copy(connection = Connection.RECONNECTING) }
                attempt++
                delay((attempt * 1_000L).coerceAtMost(5_000L))
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

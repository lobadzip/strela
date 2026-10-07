package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.app.platform.LocationSource
import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.Signature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class Connection { CONNECTING, LIVE, RECONNECTING }

data class SessionState(
    /** Who is signed in. Persisted, so the app opens straight onto the map. */
    val courier: Courier?,
    val snapshot: CourierSnapshot?,
    val connection: Connection,
    /** Keys of actions in flight, so buttons can show progress and refuse double taps. */
    val busy: Set<String> = emptySet(),
    val realGps: Boolean = false,
)

sealed interface SessionEvent {
    data class Message(val text: String, val isError: Boolean) : SessionEvent
    data class NewOrder(val order: Order) : SessionEvent
}

/**
 * The courier's side of the world: one live snapshot from the server, kept fresh over a WebSocket
 * with REST as the first paint and as the fallback when sockets are blocked.
 */
class CourierSession(
    private val backend: () -> Backend,
    private val settings: Settings,
    private val scope: CoroutineScope,
    val location: LocationSource?,
) {
    private val _state = MutableStateFlow(
        SessionState(courier = settings.courier, snapshot = null, connection = Connection.CONNECTING, realGps = settings.realGps),
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private var liveJob: Job? = null
    private var gpsJob: Job? = null

    init {
        val token = settings.token
        if (token != null && settings.courier != null) {
            backend().token = token
            startLive()
        }
    }

    // --- Sign in and out --------------------------------------------------------------------------

    suspend fun signIn(phone: String, code: String): String? = try {
        val response = backend().login(phone, code)
        backend().token = response.token
        settings.token = response.token
        settings.courier = response.courier
        _state.value = SessionState(response.courier, null, Connection.CONNECTING, realGps = settings.realGps)
        startLive()
        null
    } catch (e: ApiFailure) {
        e.message
    }

    fun signOut() {
        val wasOnline = _state.value.snapshot?.let { it.courier.online && it.active.isEmpty() } == true
        liveJob?.cancel()
        gpsJob?.cancel()
        if (wasOnline) scope.launch { runCatching { backend().setOnline(false) } }
        backend().token = null
        settings.token = null
        settings.courier = null
        _state.value = SessionState(null, null, Connection.CONNECTING, realGps = settings.realGps)
    }

    // --- Actions ----------------------------------------------------------------------------------

    fun setOnline(online: Boolean) = act("shift") { apply(backend().setOnline(online)) }

    fun accept(order: Order) = act("accept:${order.id}") {
        backend().accept(order.id)
        refresh()
    }

    fun pickUp(order: Order) = act("pickup:${order.id}") {
        backend().pickUp(order.id)
        refresh()
        _events.tryEmit(SessionEvent.Message("Заказ у вас — везите клиенту", isError = false))
    }

    fun fail(order: Order, reason: String) = act("fail:${order.id}") {
        backend().fail(order.id, reason)
        refresh()
        _events.tryEmit(SessionEvent.Message("Заказ закрыт: $reason", isError = false))
    }

    fun createDemoOrder() = act("demo-order") {
        backend().demoOrder()
        refresh()
        _events.tryEmit(SessionEvent.Message("Новый заказ появится рядом с вами", isError = false))
    }

    /** Returns the delivered order, or null after telling the courier what went wrong. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun deliver(order: Order, photo: ByteArray, signature: Signature, cashKopecks: Long): Order? =
        runAction("deliver:${order.id}") {
            val delivered = backend().deliver(order.id, DeliverRequest(Base64.encode(photo), signature, cashKopecks))
            refresh()
            delivered
        }

    fun setRealGps(enabled: Boolean) {
        settings.realGps = enabled
        _state.update { it.copy(realGps = enabled) }
        restartGps()
    }

    fun isBusy(key: String) = key in _state.value.busy

    // --- Internals --------------------------------------------------------------------------------

    private fun act(key: String, block: suspend () -> Unit) {
        if (isBusy(key)) return
        scope.launch { runAction(key) { block() } }
    }

    private suspend fun <T> runAction(key: String, block: suspend () -> T): T? {
        _state.update { it.copy(busy = it.busy + key) }
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            if (e.status == 401) signOut() else _events.tryEmit(SessionEvent.Message(e.message, isError = true))
            null
        } finally {
            _state.update { it.copy(busy = it.busy - key) }
        }
    }

    private suspend fun refresh() = apply(backend().snapshot())

    private fun apply(snapshot: CourierSnapshot) {
        val previous = _state.value.snapshot
        if (previous != null && snapshot.courier.online && snapshot.active.isEmpty()) {
            snapshot.available
                .firstOrNull { order -> previous.available.none { it.id == order.id } }
                ?.let { _events.tryEmit(SessionEvent.NewOrder(it)) }
        }
        _state.update { it.copy(snapshot = snapshot, courier = snapshot.courier) }
    }

    private fun startLive() {
        liveJob?.cancel()
        liveJob = scope.launch {
            var attempt = 0
            while (isActive) {
                _state.update { it.copy(connection = if (attempt == 0) Connection.CONNECTING else Connection.RECONNECTING) }
                try {
                    // REST first: the map paints at once even where a proxy blocks WebSockets.
                    apply(backend().snapshot())
                    backend().courierLive().collect {
                        apply(it)
                        attempt = 0
                        if (_state.value.connection != Connection.LIVE) _state.update { s -> s.copy(connection = Connection.LIVE) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiFailure) {
                    if (e.status == 401 && backend().isLocal && reviveLocalSession()) {
                        continue
                    }
                    if (e.status == 401) {
                        signOut()
                        _events.tryEmit(SessionEvent.Message("Сессия устарела, войдите снова", isError = true))
                        return@launch
                    }
                } catch (_: Throwable) {
                    // Dropped socket or server restart: reconnect below.
                }
                attempt++
                delay((attempt * 1_000L).coerceAtMost(5_000L))
            }
        }
        restartGps()
    }

    /**
     * The on-device city starts afresh with every launch, so yesterday's token means nothing to it.
     * Sign the same courier in again instead of sending them back to the login screen.
     */
    private suspend fun reviveLocalSession(): Boolean {
        val phone = settings.courier?.phone ?: return false
        val response = runCatching { backend().login(phone, DEMO_CODE) }.getOrNull() ?: return false
        backend().token = response.token
        settings.token = response.token
        return true
    }

    private fun restartGps() {
        gpsJob?.cancel()
        val source = location
        if (_state.value.courier == null) return
        if (source == null || !settings.realGps) {
            // The point is ignored when simulated: this only hands the wheel back to the server.
            scope.launch { runCatching { backend().reportLocation(LocationUpdate(GeoPoint(0.0, 0.0), null, simulated = true)) } }
            return
        }
        gpsJob = scope.launch {
            var lastSent: TimeMark? = null
            source.updates().collect { fix ->
                if (lastSent?.let { it.elapsedNow() < GPS_INTERVAL } == true) return@collect
                lastSent = TimeSource.Monotonic.markNow()
                runCatching { backend().reportLocation(LocationUpdate(fix.point, fix.heading, simulated = false)) }
            }
        }
    }

    private companion object {
        val GPS_INTERVAL = 2.5.seconds
        const val DEMO_CODE = "0000"
    }
}

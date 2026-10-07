package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.core.BakedRoutes
import io.github.lobadzip.strela.core.DeliveryService
import io.github.lobadzip.strela.core.DemoCity
import io.github.lobadzip.strela.core.OrderFactory
import io.github.lobadzip.strela.core.RouteProvider
import io.github.lobadzip.strela.core.Simulator
import io.github.lobadzip.strela.core.StraightLineRoutes
import io.github.lobadzip.strela.core.World
import io.github.lobadzip.strela.server.routing.OsrmRoutes
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlin.random.Random

/** The object graph, built by hand: five classes do not need a DI framework. */
class AppComponent(
    val config: ServerConfig,
    val routes: RouteProvider,
    val clock: () -> Long = System::currentTimeMillis,
    private val httpClient: HttpClient? = null,
) : AutoCloseable {
    val random = config.randomSeed?.let(::Random) ?: Random.Default
    val service = DeliveryService(World(DemoCity.city), routes, config.speedup, clock)
    val orders = OrderFactory(random, routes, clock)
    val simulator = Simulator(service, orders, routes, random, clock) { message, error ->
        if (error == null) log.info(message) else log.warn(message, error)
    }

    override fun close() {
        httpClient?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppComponent::class.java)

        /** Routes baked into the app (see BakeRoutes) answer first; OSRM only fills the gaps. */
        private fun baked(fallback: RouteProvider): RouteProvider {
            val text = AppComponent::class.java.getResource("/routes.json")?.readText() ?: return fallback
            return BakedRoutes(Json.decodeFromString<Map<String, String>>(text), fallback)
        }

        fun create(config: ServerConfig): AppComponent {
            val osrm = config.osrmUrl ?: return AppComponent(config, baked(StraightLineRoutes()))
            val client = HttpClient(Java) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 8_000
                    connectTimeoutMillis = 4_000
                }
            }
            val live = OsrmRoutes(osrm, client, File(config.dataDir, "routes.json"))
            return AppComponent(config, baked(live), httpClient = client)
        }
    }
}

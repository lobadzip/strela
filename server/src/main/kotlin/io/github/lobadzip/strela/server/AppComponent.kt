package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.server.demo.DemoCity
import io.github.lobadzip.strela.server.demo.OrderFactory
import io.github.lobadzip.strela.server.dispatch.DeliveryService
import io.github.lobadzip.strela.server.dispatch.World
import io.github.lobadzip.strela.server.routing.OsrmRoutes
import io.github.lobadzip.strela.server.routing.RouteProvider
import io.github.lobadzip.strela.server.routing.StraightLineRoutes
import io.github.lobadzip.strela.server.sim.Simulator
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
    val simulator = Simulator(service, orders, routes, random, clock)

    override fun close() {
        httpClient?.close()
    }

    companion object {
        fun create(config: ServerConfig): AppComponent {
            val osrm = config.osrmUrl ?: return AppComponent(config, StraightLineRoutes())
            val client = HttpClient(Java) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 8_000
                    connectTimeoutMillis = 4_000
                }
            }
            return AppComponent(config, OsrmRoutes(osrm, client, File(config.dataDir, "routes.json")), httpClient = client)
        }
    }
}

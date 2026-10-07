package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.server.api.configureApi
import io.github.lobadzip.strela.server.api.configureHttp
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(AppComponent.create(config))
    }.start(wait = true)
}

/**
 * Plugins, routes and the simulator. Tests call this with straight-line routes and a manual clock,
 * and drive the simulator themselves instead of letting it run.
 */
fun Application.module(component: AppComponent, runSimulation: Boolean = true) {
    monitor.subscribe(ApplicationStopped) { component.close() }

    configureHttp()
    configureApi(component)
    if (runSimulation) component.simulator.start(this)

    val config = component.config
    log.info(
        "Strela {} on {}:{} — routes: {}, web app: {}",
        ServerConfig.VERSION,
        config.host,
        config.port,
        config.osrmUrl ?: "straight lines",
        config.webDir?.path ?: "not built (./gradlew :composeApp:wasmJsBrowserDistribution)",
    )
}

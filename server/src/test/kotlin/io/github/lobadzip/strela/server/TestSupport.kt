package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.model.Signature
import io.github.lobadzip.strela.core.StraightLineRoutes
import java.io.File
import java.util.Base64

/** A seeded demo city on straight-line routes, real-time speed and a clock the test moves by hand. */
class TestCity(seed: Long = 42) {
    var now = 1_767_250_000_000L

    val config = ServerConfig(
        host = "localhost",
        port = 0,
        webDir = null,
        dataDir = File("build/tmp/test-data"),
        osrmUrl = null,
        speedup = 1.0,
        randomSeed = seed,
    )
    val component = AppComponent(config, StraightLineRoutes(), clock = { now })
    val service get() = component.service

    suspend fun seed() = component.simulator.seed()

    /** Runs the simulator for [seconds] of demo time in half-second ticks, as the real loop does. */
    suspend fun simulate(seconds: Int) {
        repeat(seconds * 2) {
            now += 500
            component.simulator.move(500)
            if (it % 2 == 1) component.simulator.think()
        }
    }

    /** Drives the courier's own demo GPS until they reach wherever their order sends them. */
    suspend fun driveToTarget(courierId: String) {
        repeat(20_000) {
            if (service.snapshot(courierId).navigation?.arrived != false) return
            now += 500
            service.advance(500)
        }
        error("Courier $courierId never arrived")
    }

    companion object {
        val ALEXEY = LoginRequest("+7 000 000-00-01", "0000")
        val MARINA = LoginRequest("8 (000) 000-00-02", "0000")

        val photo: String = Base64.getEncoder().encodeToString(ByteArray(2048) { (it % 251).toByte() })
        val signature = Signature(listOf(listOf(0.1f, 0.5f, 0.2f, 0.4f, 0.3f, 0.6f, 0.4f, 0.4f, 0.5f, 0.6f, 0.6f, 0.5f,
            0.7f, 0.4f, 0.8f, 0.5f, 0.9f, 0.6f)))

        fun deliverRequest(cash: Long) = DeliverRequest(photo, signature, cash)
    }
}

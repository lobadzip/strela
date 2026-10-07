package io.github.lobadzip.strela.server

import java.io.File

data class ServerConfig(
    val host: String,
    val port: Int,
    /** The Compose for Web build. Without it the server is API-only. */
    val webDir: File?,
    /** Where the route cache lives between restarts. */
    val dataDir: File,
    /** Null means straight lines: no network needed, which is what tests and offline demos want. */
    val osrmUrl: String?,
    /** Demo time runs this many times faster than real time, so a delivery takes a minute, not twenty. */
    val speedup: Double,
    val randomSeed: Long?,
) {
    companion object {
        const val VERSION = "1.0.0"

        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val webDir = env["STRELA_WEB_DIR"]?.let(::File)
                ?: listOf("composeApp/build/dist/wasmJs/productionExecutable", "web")
                    .map(::File)
                    .firstOrNull { File(it, "index.html").isFile }
            return ServerConfig(
                host = env["HOST"] ?: "0.0.0.0",
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                webDir = webDir,
                dataDir = File(env["STRELA_DATA_DIR"] ?: "data"),
                osrmUrl = when (val url = env["STRELA_OSRM_URL"]) {
                    null -> "https://router.project-osrm.org"
                    "", "off" -> null
                    else -> url.trimEnd('/')
                },
                speedup = env["STRELA_SPEEDUP"]?.toDoubleOrNull()?.coerceIn(1.0, 60.0) ?: 10.0,
                randomSeed = env["STRELA_SEED"]?.toLongOrNull(),
            )
        }
    }
}

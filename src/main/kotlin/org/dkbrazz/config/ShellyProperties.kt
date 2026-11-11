package org.dkbrazz.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "shelly")
data class ShellyProperties(
    var cloud: Cloud = Cloud(),
    var dashboard: Dashboard = Dashboard()
) {
    data class Cloud(
        var authCode: String = "",
        var clientId: String = "shelly-diy"
    ) {
        /**
         * Deprecated override of the token-exchange server. By default the server is taken from the
         * `user_api_url` claim of the authorization code, as the Shelly docs require; blank means "derive".
         */
        var region: String? = null
            set(value) {
                field = value?.trim()?.takeIf(String::isNotEmpty)
            }

        var websocketRegions: List<String> = listOf("shelly-241-eu", "shelly-242-eu")
            set(value) {
                val normalized = value.map(String::trim).filter(String::isNotEmpty).distinct()
                require(normalized.isNotEmpty()) { "At least one Shelly WebSocket region must be configured" }
                field = normalized
            }
    }

    data class Dashboard(
        var maxMeasures: Int = 150,
        /** Longest history window a single request may cover. */
        var maxRange: Duration = Duration.ofDays(90),
        /** A silence between two samples at least this long is rendered as zero in history graphs. */
        var offlineThreshold: Duration = Duration.ofSeconds(30)
    )

    fun apiUrl(region: String): String = "https://${hostOf(region)}"

    fun websocketUrl(region: String): String =
        "wss://${hostOf(region)}:6113/shelly/wss/hk_sock"

    companion object {
        const val CLOUD_DOMAIN = "shelly.cloud"

        /** `shelly-241-eu` -> `shelly-241-eu.shelly.cloud`; a value that already contains a dot is used as a host. */
        fun hostOf(region: String): String = if ('.' in region) region else "$region.$CLOUD_DOMAIN"

        /** `shelly-241-eu.shelly.cloud` -> `shelly-241-eu`; hosts outside shelly.cloud are kept whole. */
        fun regionOf(host: String): String = host.removeSuffix(".$CLOUD_DOMAIN")
    }
}

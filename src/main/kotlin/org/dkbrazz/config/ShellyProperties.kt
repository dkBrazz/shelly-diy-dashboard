package org.dkbrazz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shelly")
data class ShellyProperties(
    var cloud: Cloud = Cloud(),
    var dashboard: Dashboard = Dashboard()
) {
    data class Cloud(
        var region: String = "shelly-241-eu",
        var authCode: String = "",
        var clientId: String = "shelly-diy"
    ) {
        var websocketRegions: List<String> = listOf("shelly-241-eu", "shelly-242-eu")
            set(value) {
                val normalized = value.map(String::trim).filter(String::isNotEmpty).distinct()
                require(normalized.isNotEmpty()) { "At least one Shelly WebSocket region must be configured" }
                field = normalized
            }
    }

    data class Dashboard(
        var maxMeasures: Int = 150
    )

    val apiUrl: String
        get() = "https://${cloud.region}.shelly.cloud"

    fun websocketUrl(region: String): String =
        "wss://$region.shelly.cloud:6113/shelly/wss/hk_sock"
}

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
    )

    data class Dashboard(
        var maxMeasures: Int = 100
    )

    val apiUrl: String
        get() = "https://${cloud.region}.shelly.cloud"

    val websocketUrl: String
        get() = "wss://${cloud.region}.shelly.cloud:6113/shelly/wss/hk_sock"
}

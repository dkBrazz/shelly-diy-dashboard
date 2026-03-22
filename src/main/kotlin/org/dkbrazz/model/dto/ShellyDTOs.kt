package org.dkbrazz.model.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class ShellyEvent(
    val event: String,
    val device: ShellyDeviceDTO,
    val status: ShellyStatusDTO,
    val metadata: List<ShellyMetadataDTO>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShellyMetadataDTO(
    val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShellyDeviceDTO(
    val id: String,
    val type: String?,
    val code: String?,
    val gen: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShellyStatusDTO(
    @JsonProperty("em:0")
    val em: EmStatusDTO?,
    @JsonProperty("temperature:0")
    val temperature: TemperatureStatusDTO?,
    val ts: Double?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EmStatusDTO(
    @JsonProperty("a_voltage") val aVoltage: Double?,
    @JsonProperty("a_current") val aCurrent: Double?,
    @JsonProperty("a_act_power") val aPower: Double?,
    
    @JsonProperty("b_voltage") val bVoltage: Double?,
    @JsonProperty("b_current") val bCurrent: Double?,
    @JsonProperty("b_act_power") val bPower: Double?,
    
    @JsonProperty("c_voltage") val cVoltage: Double?,
    @JsonProperty("c_current") val cCurrent: Double?,
    @JsonProperty("c_act_power") val cPower: Double?,
    
    @JsonProperty("total_act_power") val totalPower: Double?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TemperatureStatusDTO(
    @JsonProperty("tC") val tC: Double?
)

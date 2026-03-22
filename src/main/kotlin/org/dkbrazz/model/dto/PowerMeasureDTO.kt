package org.dkbrazz.model.dto

import java.time.OffsetDateTime

data class PowerMeasureDTO(
    val time: OffsetDateTime,
    val deviceId: Int,
    val aVoltage: Double? = null,
    val aCurrent: Double? = null,
    val aPower: Double? = null,
    val bVoltage: Double? = null,
    val bCurrent: Double? = null,
    val bPower: Double? = null,
    val cVoltage: Double? = null,
    val cCurrent: Double? = null,
    val cPower: Double? = null,
    val totalPower: Double? = null,
    val temperature: Double? = null
)

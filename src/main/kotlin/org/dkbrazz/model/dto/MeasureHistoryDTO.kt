package org.dkbrazz.model.dto

import java.time.OffsetDateTime

data class MeasureHistoryDTO(
    val time: OffsetDateTime,
    val aVoltage: Double?,
    val aCurrent: Double?,
    val aPower: Double?,
    val bVoltage: Double?,
    val bCurrent: Double?,
    val bPower: Double?,
    val cVoltage: Double?,
    val cCurrent: Double?,
    val cPower: Double?,
    val totalPower: Double?,
    val temperature: Double?
)

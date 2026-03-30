package org.dkbrazz.model.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.OffsetDateTime

data class PowerMeasureId(
    var time: OffsetDateTime? = null,
    var device: Int? = null
) : Serializable

@Entity
@Table(name = "power_measures")
@IdClass(PowerMeasureId::class)
class PowerMeasure(
    @Id
    @Column(name = "time", nullable = false)
    var time: OffsetDateTime,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    var device: Device,

    @Column(name = "a_voltage", nullable = false) var aVoltage: Int,
    @Column(name = "a_current", nullable = false) var aCurrent: Int,
    @Column(name = "a_power", nullable = false) var aPower: Int,

    @Column(name = "b_voltage", nullable = false) var bVoltage: Int,
    @Column(name = "b_current", nullable = false) var bCurrent: Int,
    @Column(name = "b_power", nullable = false) var bPower: Int,

    @Column(name = "c_voltage", nullable = false) var cVoltage: Int,
    @Column(name = "c_current", nullable = false) var cCurrent: Int,
    @Column(name = "c_power", nullable = false) var cPower: Int,

    @Column(name = "total_power", nullable = false) var totalPower: Int,
    @Column(name = "temperature", nullable = false) var temperature: Int
)

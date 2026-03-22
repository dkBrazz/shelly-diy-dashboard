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

    @Column(name = "a_voltage") var aVoltage: Double? = null,
    @Column(name = "a_current") var aCurrent: Double? = null,
    @Column(name = "a_power") var aPower: Double? = null,

    @Column(name = "b_voltage") var bVoltage: Double? = null,
    @Column(name = "b_current") var bCurrent: Double? = null,
    @Column(name = "b_power") var bPower: Double? = null,

    @Column(name = "c_voltage") var cVoltage: Double? = null,
    @Column(name = "c_current") var cCurrent: Double? = null,
    @Column(name = "c_power") var cPower: Double? = null,

    @Column(name = "total_power") var totalPower: Double? = null,
    @Column(name = "temperature") var temperature: Double? = null
)

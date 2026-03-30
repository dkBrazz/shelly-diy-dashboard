package org.dkbrazz.repository

import org.dkbrazz.model.entity.Device
import org.dkbrazz.model.entity.PowerMeasure
import org.dkbrazz.model.entity.PowerMeasureId
import org.dkbrazz.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.OffsetDateTime

interface UserRepository : JpaRepository<User, Int> {
    fun findByEmail(email: String): User?
}

interface DeviceRepository : JpaRepository<Device, Int> {
    fun findByExternalId(externalId: String): Device?
}

interface PowerMeasureRepository : JpaRepository<PowerMeasure, PowerMeasureId> {
    fun findByDeviceIdAndTimeBetweenOrderByTimeAsc(
        deviceId: Int, 
        start: OffsetDateTime, 
        end: OffsetDateTime
    ): List<PowerMeasure>
    
    fun findFirstByDeviceIdOrderByTimeDesc(deviceId: Int): PowerMeasure?

    @Query(value = """
        SELECT 
            time_bucket(CAST(:bucketInterval AS INTERVAL), time) AS bucketTime,
            AVG(a_voltage)::DOUBLE PRECISION / 1000.0 AS aVoltage,
            AVG(a_current)::DOUBLE PRECISION / 1000.0 AS aCurrent,
            AVG(a_power)::DOUBLE PRECISION / 1000.0 AS aPower,
            AVG(b_voltage)::DOUBLE PRECISION / 1000.0 AS bVoltage,
            AVG(b_current)::DOUBLE PRECISION / 1000.0 AS bCurrent,
            AVG(b_power)::DOUBLE PRECISION / 1000.0 AS bPower,
            AVG(c_voltage)::DOUBLE PRECISION / 1000.0 AS cVoltage,
            AVG(c_current)::DOUBLE PRECISION / 1000.0 AS cCurrent,
            AVG(c_power)::DOUBLE PRECISION / 1000.0 AS cPower,
            AVG(total_power)::DOUBLE PRECISION / 1000.0 AS totalPower,
            AVG(temperature)::DOUBLE PRECISION / 1000.0 AS temperature
        FROM power_measures
        WHERE device_id = :deviceId AND time >= :start AND time <= :end
        GROUP BY bucketTime
        ORDER BY bucketTime ASC
    """, nativeQuery = true)
    fun findHistoryAggregated(
        deviceId: Int,
        start: OffsetDateTime,
        end: OffsetDateTime,
        bucketInterval: String
    ): List<MeasureHistoryProjection>
}

interface MeasureHistoryProjection {
    fun getBucketTime(): Instant
    fun getAVoltage(): Double?
    fun getACurrent(): Double?
    fun getAPower(): Double?
    fun getBVoltage(): Double?
    fun getBCurrent(): Double?
    fun getBPower(): Double?
    fun getCVoltage(): Double?
    fun getCCurrent(): Double?
    fun getCPower(): Double?
    fun getTotalPower(): Double?
    fun getTemperature(): Double?
}

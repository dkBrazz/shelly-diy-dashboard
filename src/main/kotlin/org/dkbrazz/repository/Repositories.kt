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
        WITH buckets AS (
            SELECT
                time_bucket_gapfill(CAST(:bucketInterval AS INTERVAL), time) AS bucket_time,
                MIN(time) AS first_sample,
                MAX(time) AS last_sample,
                AVG(a_voltage)::DOUBLE PRECISION / 1000.0 AS a_voltage,
                AVG(a_current)::DOUBLE PRECISION / 1000.0 AS a_current,
                AVG(a_power)::DOUBLE PRECISION / 1000.0 AS a_power,
                AVG(b_voltage)::DOUBLE PRECISION / 1000.0 AS b_voltage,
                AVG(b_current)::DOUBLE PRECISION / 1000.0 AS b_current,
                AVG(b_power)::DOUBLE PRECISION / 1000.0 AS b_power,
                AVG(c_voltage)::DOUBLE PRECISION / 1000.0 AS c_voltage,
                AVG(c_current)::DOUBLE PRECISION / 1000.0 AS c_current,
                AVG(c_power)::DOUBLE PRECISION / 1000.0 AS c_power,
                AVG(total_power)::DOUBLE PRECISION / 1000.0 AS total_power,
                AVG(temperature)::DOUBLE PRECISION / 1000.0 AS temperature
            FROM power_measures
            WHERE device_id = :deviceId
              AND time >= :start
              AND time <= :end
            GROUP BY bucket_time
        ),
        gaps AS (
            SELECT
                b.*,
                -- An empty bucket counts as device downtime only when the silence between the
                -- nearest real samples (or the range bounds) reaches the offline threshold.
                -- Shorter gaps stay NULL so the graphs connect across them.
                last_sample IS NULL
                    AND COALESCE(
                            MIN(first_sample) OVER (ORDER BY bucket_time ROWS BETWEEN 1 FOLLOWING AND UNBOUNDED FOLLOWING),
                            :end
                        )
                      - COALESCE(
                            MAX(last_sample) OVER (ORDER BY bucket_time ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),
                            :start
                        ) >= CAST(:gapThreshold AS INTERVAL) AS offline
            FROM buckets b
        )
        SELECT
            bucket_time AS bucketTime,
            CASE WHEN offline THEN 0.0 ELSE a_voltage END AS aVoltage,
            CASE WHEN offline THEN 0.0 ELSE a_current END AS aCurrent,
            CASE WHEN offline THEN 0.0 ELSE a_power END AS aPower,
            CASE WHEN offline THEN 0.0 ELSE b_voltage END AS bVoltage,
            CASE WHEN offline THEN 0.0 ELSE b_current END AS bCurrent,
            CASE WHEN offline THEN 0.0 ELSE b_power END AS bPower,
            CASE WHEN offline THEN 0.0 ELSE c_voltage END AS cVoltage,
            CASE WHEN offline THEN 0.0 ELSE c_current END AS cCurrent,
            CASE WHEN offline THEN 0.0 ELSE c_power END AS cPower,
            CASE WHEN offline THEN 0.0 ELSE total_power END AS totalPower,
            -- An unknown temperature is not 0 °C, so it stays NULL in gaps.
            temperature
        FROM gaps
        ORDER BY bucketTime ASC
    """, nativeQuery = true)
    fun findHistoryAggregated(
        deviceId: Int,
        start: OffsetDateTime,
        end: OffsetDateTime,
        bucketInterval: String,
        gapThreshold: String
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

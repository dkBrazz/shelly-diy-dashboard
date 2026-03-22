package org.dkbrazz.service

import org.dkbrazz.model.dto.MeasureHistoryDTO
import org.dkbrazz.model.dto.PowerMeasureDTO
import org.dkbrazz.model.entity.PowerMeasure
import org.dkbrazz.repository.DeviceRepository
import org.dkbrazz.repository.PowerMeasureRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class MeasureService(
    private val measureRepository: PowerMeasureRepository,
    private val deviceRepository: DeviceRepository
) {
    private val logger = LoggerFactory.getLogger(MeasureService::class.java)

    private fun Double?.roundToOne(): Double? = this?.let {
        BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    fun saveMeasure(measure: PowerMeasure) {
        measureRepository.save(measure)
    }

    fun getLatestMeasures(): List<PowerMeasureDTO> {
        return deviceRepository.findAll().mapNotNull { device ->
            measureRepository.findFirstByDeviceIdOrderByTimeDesc(device.id!!)?.let { m ->
                PowerMeasureDTO(
                    time = m.time,
                    deviceId = device.id!!,
                    aVoltage = m.aVoltage.roundToOne(),
                    aCurrent = m.aCurrent.roundToOne(),
                    aPower = m.aPower.roundToOne(),
                    bVoltage = m.bVoltage.roundToOne(),
                    bCurrent = m.bCurrent.roundToOne(),
                    bPower = m.bPower.roundToOne(),
                    cVoltage = m.cVoltage.roundToOne(),
                    cCurrent = m.cCurrent.roundToOne(),
                    cPower = m.cPower.roundToOne(),
                    totalPower = m.totalPower.roundToOne(),
                    temperature = m.temperature.roundToOne()
                )
            }
        }
    }

    fun getHistory(deviceId: Int, start: OffsetDateTime, end: OffsetDateTime, measures: Int): List<MeasureHistoryDTO> {
        val duration = Duration.between(start, end)
        val intervalSeconds = max(1.0, duration.toSeconds().toDouble() / measures)
        val bucketInterval = "$intervalSeconds seconds"

        logger.debug("Calculating measure history for device $deviceId from $start to $end with $measures measures, bucket interval: $bucketInterval")

        return measureRepository.findHistoryAggregated(deviceId, start, end, bucketInterval).map { p ->
            MeasureHistoryDTO(
                time = p.getBucketTime().atOffset(ZoneOffset.UTC),
                aVoltage = p.getAVoltage().roundToOne(),
                aCurrent = p.getACurrent().roundToOne(),
                aPower = p.getAPower().roundToOne(),
                bVoltage = p.getBVoltage().roundToOne(),
                bCurrent = p.getBCurrent().roundToOne(),
                bPower = p.getBPower().roundToOne(),
                cVoltage = p.getCVoltage().roundToOne(),
                cCurrent = p.getCCurrent().roundToOne(),
                cPower = p.getCPower().roundToOne(),
                totalPower = p.getTotalPower().roundToOne(),
                temperature = p.getTemperature().roundToOne()
            )
        }
    }
}

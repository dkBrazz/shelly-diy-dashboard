package org.dkbrazz.service

import org.dkbrazz.model.dto.ShellyEvent
import org.dkbrazz.model.entity.PowerMeasure
import org.dkbrazz.repository.DeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ShellyEventProcessor(
    private val deviceService: DeviceService,
    private val measureService: MeasureService,
    private val deviceRepository: DeviceRepository
) {
    private val logger = LoggerFactory.getLogger(ShellyEventProcessor::class.java)

    @Async
    fun processEvent(event: ShellyEvent) {
        if (event.event != "Shelly:StatusOnChange") {
            logger.debug("Skipping event with type ${event.event}")
            return
        }

        try {
            logger.debug("Processing Shelly event: $event")
            val name = event.metadata?.firstOrNull { it.name != null }?.name
            val surrogateId = deviceService.getOrCreate(event.device, name)
            val device = deviceRepository.findById(surrogateId).orElseThrow()

            // ts is a Double in seconds with sub-second precision (e.g., 1773223720.09)
            val ts = event.status.ts ?: (System.currentTimeMillis() / 1000.0)
            val bd = BigDecimal.valueOf(ts)
            val seconds = bd.toLong()
            val nanos = bd.subtract(BigDecimal.valueOf(seconds))
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .toLong()
            val time = OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC)

            val em = event.status.em
            val temp = event.status.temperature

            val measure = PowerMeasure(
                time = time,
                device = device,
                aVoltage = em?.aVoltage,
                aCurrent = em?.aCurrent,
                aPower = em?.aPower,
                bVoltage = em?.bVoltage,
                bCurrent = em?.bCurrent,
                bPower = em?.bPower,
                cVoltage = em?.cVoltage,
                cCurrent = em?.cCurrent,
                cPower = em?.cPower,
                totalPower = em?.totalPower,
                temperature = temp?.tC
            )

            measureService.saveMeasure(measure)
        } catch (e: Exception) {
            logger.error("Failed to process Shelly event", e)
        }
    }
}

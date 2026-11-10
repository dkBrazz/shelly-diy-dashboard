package org.dkbrazz.service

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.model.scaleMeasure
import org.dkbrazz.model.entity.Device
import org.dkbrazz.model.entity.PowerMeasure
import org.dkbrazz.repository.DeviceRepository
import org.dkbrazz.repository.PowerMeasureRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MeasureServiceTest {
    private val measureRepository = mock(PowerMeasureRepository::class.java)
    private val deviceRepository = mock(DeviceRepository::class.java)
    private val props = ShellyProperties()
    private val measureService = MeasureService(measureRepository, deviceRepository, props)

    @Test
    fun `test interval calculation for 24 hours and 500 measures`() {
        val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusHours(24)
        val measures = 500
        
        // 86400 / 500 = 172.8
        
        `when`(measureRepository.findHistoryAggregated(1, start, end, "172.8 seconds", "60.0 seconds"))
            .thenReturn(emptyList())
            
        measureService.getHistory(1, start, end, measures)
        
        verify(measureRepository).findHistoryAggregated(1, start, end, "172.8 seconds", "60.0 seconds")
    }

    @Test
    fun `test interval calculation for 24 hours and 100 measures`() {
        val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusHours(24)
        val measures = 100
        
        // 86400 / 100 = 864.0
        
        `when`(measureRepository.findHistoryAggregated(1, start, end, "864.0 seconds", "60.0 seconds"))
            .thenReturn(emptyList())
            
        measureService.getHistory(1, start, end, measures)
        
        verify(measureRepository).findHistoryAggregated(1, start, end, "864.0 seconds", "60.0 seconds")
    }

    @Test
    fun `test configured offline threshold is passed as gap threshold`() {
        props.dashboard.offlineThreshold = Duration.ofMillis(90_500)
        val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusMinutes(5)

        `when`(measureRepository.findHistoryAggregated(1, start, end, "2.0 seconds", "90.5 seconds"))
            .thenReturn(emptyList())

        measureService.getHistory(1, start, end, 150)

        verify(measureRepository).findHistoryAggregated(1, start, end, "2.0 seconds", "90.5 seconds")
    }

    @Test
    fun `test measure values are rounded to 1 decimal place`() {
        val deviceId = 1
        val device = Device(id = deviceId, externalId = "123", name = "Test Device")
        val measure = PowerMeasure(
            time = OffsetDateTime.now(),
            device = device,
            aVoltage = scaleMeasure(230.1234),
            aCurrent = scaleMeasure(1.5678),
            aPower = scaleMeasure(360.9999),
            bVoltage = scaleMeasure(229.8765),
            bCurrent = scaleMeasure(1.4321),
            bPower = scaleMeasure(328.7654),
            cVoltage = scaleMeasure(231.0001),
            cCurrent = scaleMeasure(1.5001),
            cPower = scaleMeasure(346.5001),
            totalPower = scaleMeasure(360.9999),
            temperature = scaleMeasure(35.45)
        )

        `when`(deviceRepository.findAll()).thenReturn(listOf(device))
        `when`(measureRepository.findFirstByDeviceIdOrderByTimeDesc(deviceId)).thenReturn(measure)

        val latest = measureService.getLatestMeasures().first()

        assertEquals(230.1, latest.aVoltage)
        assertEquals(1.6, latest.aCurrent)
        assertEquals(361.0, latest.aPower)
        assertEquals(361.0, latest.totalPower)
        assertEquals(35.5, latest.temperature)
    }
}

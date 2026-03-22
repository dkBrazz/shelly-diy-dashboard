package org.dkbrazz.service

import org.dkbrazz.model.entity.Device
import org.dkbrazz.model.entity.PowerMeasure
import org.dkbrazz.repository.DeviceRepository
import org.dkbrazz.repository.PowerMeasureRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MeasureServiceTest {
    private val measureRepository = mock(PowerMeasureRepository::class.java)
    private val deviceRepository = mock(DeviceRepository::class.java)
    private val measureService = MeasureService(measureRepository, deviceRepository)

    @Test
    fun `test interval calculation for 24 hours and 500 measures`() {
        val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusHours(24)
        val measures = 500
        
        // 86400 / 500 = 172.8
        
        `when`(measureRepository.findHistoryAggregated(1, start, end, "172.8 seconds"))
            .thenReturn(emptyList())
            
        measureService.getHistory(1, start, end, measures)
        
        verify(measureRepository).findHistoryAggregated(1, start, end, "172.8 seconds")
    }

    @Test
    fun `test interval calculation for 24 hours and 100 measures`() {
        val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusHours(24)
        val measures = 100
        
        // 86400 / 100 = 864.0
        
        `when`(measureRepository.findHistoryAggregated(1, start, end, "864.0 seconds"))
            .thenReturn(emptyList())
            
        measureService.getHistory(1, start, end, measures)
        
        verify(measureRepository).findHistoryAggregated(1, start, end, "864.0 seconds")
    }

    @Test
    fun `test measure values are rounded to 1 decimal place`() {
        val deviceId = 1
        val device = Device(id = deviceId, externalId = "123", name = "Test Device")
        val measure = PowerMeasure(
            time = OffsetDateTime.now(),
            device = device,
            aVoltage = 230.1234,
            aCurrent = 1.5678,
            aPower = 360.9999,
            totalPower = 360.9999,
            temperature = 35.45
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

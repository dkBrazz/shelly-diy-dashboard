package org.dkbrazz.controller

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.service.DeviceService
import org.dkbrazz.service.MeasureService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DashboardControllerTest {
    private val deviceService = mock(DeviceService::class.java)
    private val measureService = mock(MeasureService::class.java)
    private val props = ShellyProperties()
    private val controller = DashboardController(deviceService, measureService, props)

    private val start = OffsetDateTime.of(2024, 3, 21, 0, 0, 0, 0, ZoneOffset.UTC)
    private val end = start.plusHours(24)

    @Test
    fun `defaults measures to the configured maximum`() {
        controller.getHistory(1, start, end, null)

        verify(measureService).getHistory(1, start, end, props.dashboard.maxMeasures)
    }

    @Test
    fun `passes measures within bounds unchanged`() {
        controller.getHistory(1, start, end, 42)

        verify(measureService).getHistory(1, start, end, 42)
    }

    @Test
    fun `clamps measures above the configured maximum`() {
        controller.getHistory(1, start, end, props.dashboard.maxMeasures + 1_000)

        verify(measureService).getHistory(1, start, end, props.dashboard.maxMeasures)
    }

    @Test
    fun `clamps zero and negative measures to one`() {
        controller.getHistory(1, start, end, 0)
        controller.getHistory(2, start, end, -5)

        verify(measureService).getHistory(1, start, end, 1)
        verify(measureService).getHistory(2, start, end, 1)
    }

    @Test
    fun `rejects start equal to end`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.getHistory(1, start, start, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verifyNoInteractions(measureService)
    }

    @Test
    fun `rejects start after end`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.getHistory(1, end, start, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verifyNoInteractions(measureService)
    }

    @Test
    fun `compares start and end as instants regardless of offset`() {
        val sameInstantLaterOffset = end.withOffsetSameInstant(ZoneOffset.ofHours(3))

        controller.getHistory(1, start, sameInstantLaterOffset, null)

        verify(measureService).getHistory(1, start, sameInstantLaterOffset, props.dashboard.maxMeasures)
    }

    @Test
    fun `rejects ranges wider than the configured maximum`() {
        props.dashboard.maxRange = Duration.ofDays(7)

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.getHistory(1, start, start.plusDays(7).plusSeconds(1), null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verifyNoInteractions(measureService)
    }

    @Test
    fun `accepts a range exactly at the configured maximum`() {
        props.dashboard.maxRange = Duration.ofDays(7)
        val boundaryEnd = start.plusDays(7)

        controller.getHistory(1, start, boundaryEnd, null)

        verify(measureService).getHistory(1, start, boundaryEnd, props.dashboard.maxMeasures)
    }

    @Test
    fun `default maximum range accepts a ninety day window`() {
        val ninetyDays = start.plusDays(90)

        controller.getHistory(1, start, ninetyDays, null)

        verify(measureService).getHistory(1, start, ninetyDays, props.dashboard.maxMeasures)
    }
}

package org.dkbrazz.controller

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.model.dto.MeasureHistoryDTO
import org.dkbrazz.model.dto.PowerMeasureDTO
import org.dkbrazz.model.entity.Device
import org.dkbrazz.service.DeviceService
import org.dkbrazz.service.MeasureService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/devices")
class DashboardController(
    private val deviceService: DeviceService,
    private val measureService: MeasureService,
    private val props: ShellyProperties
) {
    @GetMapping
    fun listDevices(): List<Device> {
        return deviceService.getAllDevices()
    }

    @GetMapping("/latest")
    fun latestMeasures(): List<PowerMeasureDTO> {
        return measureService.getLatestMeasures()
    }

    @GetMapping("/{deviceId}/history")
    fun getHistory(
        @PathVariable deviceId: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: OffsetDateTime,
        @RequestParam(required = false) measures: Int?
    ): List<MeasureHistoryDTO> {
        val limit = measures ?: props.dashboard.maxMeasures
        return measureService.getHistory(deviceId, start, end, limit)
    }
}

package org.dkbrazz.service

import jakarta.annotation.PostConstruct
import org.dkbrazz.model.dto.ShellyDeviceDTO
import org.dkbrazz.model.entity.Device
import org.dkbrazz.repository.DeviceRepository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class DeviceService(private val deviceRepository: DeviceRepository) {
    private val idMap = ConcurrentHashMap<String, Int>()

    @PostConstruct
    fun init() {
        deviceRepository.findAll().forEach {
            it.id?.let { id ->
                idMap[it.externalId] = id
            }
        }
    }

    fun getOrCreate(dto: ShellyDeviceDTO, name: String? = null): Int {
        return idMap.computeIfAbsent(dto.id) { externalId ->
            val existing = deviceRepository.findByExternalId(externalId)
            if (existing != null) {
                if (existing.name == null && name != null) {
                    existing.name = name
                    deviceRepository.save(existing)
                }
                existing.id!!
            } else {
                val newDevice = Device(
                    externalId = externalId,
                    type = dto.type,
                    code = dto.code,
                    name = name,
                    gen = dto.gen
                )
                deviceRepository.save(newDevice).id!!
            }
        }
    }
    
    fun getAllDevices(): List<Device> = deviceRepository.findAll().sortedBy { it.name }
}

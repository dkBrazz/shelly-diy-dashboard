package org.dkbrazz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
class ShellyApplication

fun main(args: Array<String>) {
    runApplication<ShellyApplication>(*args)
}

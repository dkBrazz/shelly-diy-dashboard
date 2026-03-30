package org.dkbrazz.model

import java.math.BigDecimal
import java.math.RoundingMode

private const val MEASURE_SCALE_FACTOR = 1000.0

fun scaleMeasure(value: Double): Int =
    BigDecimal.valueOf(value)
        .movePointRight(3)
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact()

fun requireScaledMeasure(name: String, value: Double?): Int =
    scaleMeasure(requireNotNull(value) { "Missing required measure: $name" })

fun unscaleMeasure(value: Int): Double = value / MEASURE_SCALE_FACTOR

fun unscaleMeasure(value: Int?): Double? = value?.toDouble()?.div(MEASURE_SCALE_FACTOR)

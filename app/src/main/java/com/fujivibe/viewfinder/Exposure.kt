package com.fujivibe.viewfinder

import java.util.Locale
import kotlin.math.roundToLong

private const val NANOS_PER_SECOND = 1_000_000_000L

/** Longest shutter the dial offers; longer exposures make the live preview unusably slow. */
const val MAX_MANUAL_EXPOSURE_NS = NANOS_PER_SECOND

/** Standard third-stop ISO values, as printed on camera dials. */
private val STANDARD_ISOS = listOf(
    25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
    1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600,
)

/** Standard third-stop shutter speeds, fastest first: fractions of a second, then whole seconds. */
private val STANDARD_SHUTTERS_NS: List<Long> =
    listOf(
        8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320,
        250, 200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4,
    ).map { NANOS_PER_SECOND / it } +
        listOf(0.3, 0.4, 0.5, 0.6, 0.8, 1.0).map { (it * NANOS_PER_SECOND).roundToLong() }

/** One exposure setting: sensor sensitivity and how long the sensor collects light. */
data class Exposure(val iso: Int, val exposureTimeNs: Long)

/** The ISO values the dial offers for a camera supporting [min]..[max], lowest first. */
fun isoStops(min: Int, max: Int): List<Int> =
    (listOf(min) + STANDARD_ISOS.filter { it > min && it < max } + listOf(max)).distinct()

/** The shutter speeds the dial offers for a camera supporting [minNs]..[maxNs], fastest first. */
fun shutterStops(minNs: Long, maxNs: Long): List<Long> {
    val cappedMax = maxNs.coerceAtMost(MAX_MANUAL_EXPOSURE_NS)
    return STANDARD_SHUTTERS_NS.filter { it in minNs..cappedMax }
}

fun isoReadout(iso: Int): String = "ISO $iso"

/** "1/250" for fractions of a second, "0.5s" / "1s" from 0.3s up, like a camera's display. */
fun shutterReadout(exposureTimeNs: Long): String {
    if (exposureTimeNs >= 3 * NANOS_PER_SECOND / 10) {
        val seconds = String.format(Locale.US, "%.1f", exposureTimeNs.toDouble() / NANOS_PER_SECOND)
        return seconds.removeSuffix(".0") + "s"
    }
    return "1/" + (NANOS_PER_SECOND.toDouble() / exposureTimeNs).roundToLong()
}

/**
 * The exposure to force on the camera for the user's dial choices, or null to leave the camera
 * on its automatic exposure (both on Auto). With one choice on Auto, that one is derived from
 * [metered] so the photo keeps the same overall brightness (ISO x time held constant), clamped
 * to what the camera supports.
 */
fun resolveExposure(
    isoChoice: Int?,
    shutterChoiceNs: Long?,
    metered: Exposure,
    isoRange: IntRange,
    exposureTimeRangeNs: LongRange,
): Exposure? {
    val lightProduct = metered.iso.toDouble() * metered.exposureTimeNs
    return when {
        isoChoice == null && shutterChoiceNs == null -> null
        isoChoice != null && shutterChoiceNs != null -> Exposure(isoChoice, shutterChoiceNs)
        isoChoice != null -> Exposure(
            isoChoice,
            (lightProduct / isoChoice).roundToLong().coerceIn(exposureTimeRangeNs),
        )
        else -> Exposure(
            (lightProduct / shutterChoiceNs!!).toInt().coerceIn(isoRange),
            shutterChoiceNs,
        )
    }
}

/**
 * How much brighter (+) or darker (-) [exposure] makes the photo than the camera's own
 * [metered] choice did, in stops (each stop doubles or halves the light).
 */
fun exposureOffsetStops(exposure: Exposure, metered: Exposure): Double =
    kotlin.math.log2(
        (exposure.iso.toDouble() * exposure.exposureTimeNs) /
            (metered.iso.toDouble() * metered.exposureTimeNs)
    )

/** "+1.0 EV" / "-0.7 EV", or "0 EV" when within a twentieth of a stop of the metered brightness. */
fun brightnessReadout(offsetStops: Double): String =
    if (kotlin.math.abs(offsetStops) < 0.05) "0 EV" else String.format(Locale.US, "%+.1f EV", offsetStops)

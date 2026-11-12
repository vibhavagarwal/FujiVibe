package com.fujivibe.recipe

/**
 * The camera stores High ISO noise reduction as a code, not a step. Read from an X-T5's own custom
 * slots: 0 is 0x2000 and -4 is 0x8000. The steps in between are not known for certain, so they are
 * not guessed; a recipe asking for one is reported as unsupported.
 */
object NoiseReductionCodes {
    fun encode(step: Int): Int? = when (step) {
        0 -> 0x2000
        -4 -> 0x8000
        else -> null
    }
}

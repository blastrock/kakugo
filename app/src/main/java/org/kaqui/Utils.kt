package org.kaqui

import org.kaqui.model.*
import java.util.Random

fun getBackgroundFromScore(score: Double) =
        when (score) {
            in 0.0f..BAD_WEIGHT -> R.drawable.round_red
            in BAD_WEIGHT..GOOD_WEIGHT -> R.drawable.round_yellow
            in GOOD_WEIGHT..1.0f -> R.drawable.round_green
            else -> R.drawable.round_red
        }

fun <T> MutableList<T>.shuffle() {
    val rg = Random()
    for (i in this.size - 1 downTo 1) {
        val target = rg.nextInt(i)
        val tmp = this[i]
        this[i] = this[target]
        this[target] = tmp
    }
}

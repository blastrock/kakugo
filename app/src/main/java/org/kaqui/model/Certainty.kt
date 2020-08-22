package org.kaqui.model

enum class Certainty(val value: Int) {
    DONTKNOW(0),
    MAYBE(1),
    SURE(2);

    companion object {
        private val map = values().associateBy(Certainty::value)
        fun fromInt(type: Int) = map.getValue(type)
    }
}
package org.kaqui.kaqui

import android.content.ContentValues.TAG
import android.util.Log
import org.xmlpull.v1.XmlPullParser

data class Kanji(
        var kanji: String,
        var readings: List<Reading>,
        var meanings: List<String>,
        var jlptLevel: Int,
        var weight: Float,
        var enabled: Boolean
)
data class Reading(var readingType: String, var reading: String)

fun <T> T?.fmap(f: (T) -> Unit) {
    if (this != null)
        f(this)
}

private fun checkAttrs(xpp: XmlPullParser, attrs: Map<String, String?>): Boolean {
    return attrs.map { (k, v) -> xpp.getAttributeValue(null, k) == v }.all { it }
}

private fun parseText(xpp: XmlPullParser, tag: String, attrs: Map<String, String?>? = null): String? {
    var ret: String? = null
    if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == tag && (attrs == null || checkAttrs(xpp, attrs))) {
        while (xpp.eventType != XmlPullParser.END_TAG) {
            if (xpp.eventType == XmlPullParser.TEXT)
                ret = xpp.text
            else if (xpp.eventType == XmlPullParser.END_TAG && xpp.name == tag)
                break
            xpp.next()
        }
    }
    return ret
}

private fun parseFreq(xpp: XmlPullParser): Int? {
    var ret: Int? = null
    if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "freq") {
        while (xpp.eventType != XmlPullParser.END_TAG) {
            if (xpp.eventType == XmlPullParser.TEXT)
                ret = xpp.text.toInt()
            else if (xpp.eventType == XmlPullParser.END_TAG && xpp.name == "freq")
                break
            xpp.next()
        }
    }
    return ret
}

private fun parseCharacter(xpp: XmlPullParser, jlptLevels: Map<Int, String>): Kanji? {
    if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "character") {
        var literal: String? = null
        val readings = mutableListOf<Reading>()
        val meanings = mutableListOf<String>()
        var freq: Int? = null
        while (!(xpp.eventType == XmlPullParser.END_TAG && xpp.name == "character")) {
            literal = parseText(xpp, "literal") ?: literal
            parseText(xpp, "reading", mapOf("r_type" to "ja_on")).fmap { readings.add(Reading("ja_on", it)) }
            parseText(xpp, "reading", mapOf("r_type" to "ja_kun")).fmap { readings.add(Reading("ja_kun", it)) }
            parseText(xpp, "meaning", mapOf("m_lang" to null)).fmap { meanings.add(it) }
            parseFreq(xpp).fmap { freq = it }
            xpp.next()
        }
        if (literal == null || freq == null)
            return null
        var jlptLevel: Int = 0
        for ((level, kanjis) in jlptLevels) {
            if (literal in kanjis) {
                jlptLevel = level
                break
            }
        }
        return Kanji(literal, readings, meanings, jlptLevel, 0.0f,true)
    }
    return null
}

fun parseXml(xpp: XmlPullParser): List<Kanji> {
    val jlptLevels = getJlptLevels()
    val list = mutableListOf<Kanji>()
    while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
        parseCharacter(xpp, jlptLevels).fmap {
            list.add(it)
        }
        xpp.next()
        if (list.size % 100 == 0)
            Log.v(TAG, "Parsed ${list.size} kanjis")
    }
    return list
}

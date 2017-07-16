package org.kaqui.kaqui

import org.xmlpull.v1.XmlPullParser

data class Kanji(var kanji: String, var readings: List<Reading>, var meanings: List<String>)
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
            else if (xpp.eventType == XmlPullParser.END_TAG)
                break
            xpp.next()
        }
    }
    return ret
}

private fun parseCharacter(xpp: XmlPullParser): Kanji? {
    if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "character") {
        var literal: String? = null
        val readings = mutableListOf<Reading>()
        val meanings = mutableListOf<String>()
        while (!(xpp.eventType == XmlPullParser.END_TAG && xpp.name == "character")) {
            literal = parseText(xpp, "literal") ?: literal
            parseText(xpp, "reading", mapOf("r_type" to "ja_on")).fmap { readings.add(Reading("ja_on", it)) }
            parseText(xpp, "reading", mapOf("r_type" to "ja_kun")).fmap { readings.add(Reading("ja_kun", it)) }
            parseText(xpp, "meaning", mapOf("m_lang" to null)).fmap { meanings.add(it) }
            xpp.next()
        }
        if (literal == null)
            return null
        return Kanji(literal, readings, meanings)
    }
    return null
}

fun parseXml(xpp: XmlPullParser): List<Kanji> {
    val list = mutableListOf<Kanji>()
    while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
        parseCharacter(xpp).fmap { list.add(it) }
        xpp.next()
    }
    return list
}
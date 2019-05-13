package org.kaqui.testactivities

import android.view.View
import org.kaqui.TestEngine
import org.kaqui.model.Certainty
import org.kaqui.model.Item
import org.kaqui.model.TestType

interface TestFragmentHolder {
    val testEngine: TestEngine
    val testType: TestType

    fun onGoodAnswer(button: View, certainty: Certainty)
    fun onWrongAnswer(button: View, wrong: Item?)
    fun nextQuestion()
}

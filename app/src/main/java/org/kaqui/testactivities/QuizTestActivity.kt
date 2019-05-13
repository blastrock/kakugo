package org.kaqui.testactivities

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.*

class QuizTestActivity : TestActivityBase(), TestFragmentHolder {
    companion object {
        private const val TAG = "QuizTestActivity"
    }

    private lateinit var testLayout: TestLayout
    private lateinit var testFragment: TestFragment

    override val historyScrollView: NestedScrollView get() = testLayout.historyScrollView
    override val historyActionButton: FloatingActionButton get() = testLayout.historyActionButton
    override val historyView: LinearLayout get() = testLayout.historyView
    override val sessionScore: TextView get() = testLayout.sessionScore
    override val mainView: View get() = testLayout.mainView
    override val mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout get() = testLayout.mainCoordinatorLayout

    override val testType
        get() = intent.extras!!.getSerializable("test_type") as TestType

    override val currentQuestion: Item
        get() = testEngine.currentQuestion
    override val currentAnswers: List<Item>
        get() = testEngine.currentAnswers
    override val currentDebugData: TestEngine.DebugData?
        get() = testEngine.currentDebugData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        testLayout = TestLayout(this) {
            frameLayout {
                id = R.id.main_test_block
            }
        }

        setUpGui(savedInstanceState)

        supportFragmentManager.transaction {
            val testFragment = QuizTestFragment.newInstance()
            this@QuizTestActivity.testFragment = testFragment
            replace(R.id.main_test_block, testFragment)
        }
    }

    override fun onGoodAnswer(button: View, certainty: Certainty) {
        testEngine.markAnswer(certainty)
        finishQuestion(button, certainty)
    }

    override fun onWrongAnswer(button: View, wrong: Item?) {
        testEngine.markAnswer(Certainty.DONTKNOW, wrong)
        finishQuestion(button, Certainty.DONTKNOW)
    }

    private fun finishQuestion(button: View, certainty: Certainty) {
        val offsetViewBounds = Rect()
        button.getDrawingRect(offsetViewBounds)
        testLayout.mainCoordinatorLayout.offsetDescendantRectToMyCoords(button, offsetViewBounds)
        testLayout.overlay.trigger(offsetViewBounds.centerX(), offsetViewBounds.centerY(), ContextCompat.getColor(this, certainty.toColorRes()))

        testEngine.prepareNewQuestion()
        testFragment.refreshQuestion()

        showCurrentQuestion()
    }
}

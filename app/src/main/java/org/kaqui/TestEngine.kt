package org.kaqui

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.preference.PreferenceManager
import org.kaqui.model.*
import java.util.*

class TestEngine(
        context: Context,
        private val db: Database,
        private val testTypes: List<TestType>,
        private val goodAnswerCallback: (correct: Item, probabilityData: DebugData?, refresh: Boolean) -> Unit,
        private val wrongAnswerCallback: (correct: Item, probabilityData: DebugData?, wrong: Item, refresh: Boolean) -> Unit,
        private val unknownAnswerCallback: (correct: Item, probabilityData: DebugData?, refresh: Boolean) -> Unit) {
    private sealed class HistoryLine {
        data class Correct(val itemId: Int) : HistoryLine()
        data class Unknown(val itemId: Int) : HistoryLine()
        data class Incorrect(val correctItemId: Int, val answerItemId: Int) : HistoryLine()
    }

    companion object {
        private const val TAG = "TestEngine"
        private const val LAST_QUESTIONS_TO_AVOID_COUNT = 6
        const val MAX_HISTORY_SIZE = 40

        fun getItemView(context: Context, db: Database, testType: TestType): LearningDbView =
                when (testType) {
                    TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_HIRAGANA, TestType.HIRAGANA_DRAWING -> db.getHiraganaView(getKnowledgeType(testType))
                    TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_KATAKANA, TestType.KATAKANA_DRAWING -> db.getKatakanaView(getKnowledgeType(testType))

                    TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.KANJI_DRAWING, TestType.KANJI_COMPOSITION -> db.getKanjiView(getKnowledgeType(testType))

                    TestType.WORD_TO_READING, TestType.READING_TO_WORD -> db.getWordView(getKnowledgeType(testType), withKanaAlone = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("kana_words", true) == false)
                    TestType.WORD_TO_MEANING, TestType.MEANING_TO_WORD -> db.getWordView(getKnowledgeType(testType))
                }
    }

    data class DebugData(
            var probabilityData: SrsCalculator.ProbabilityData,
            var probaParamsStage1: SrsCalculator.ProbaParamsStage1,
            var probaParamsStage2: SrsCalculator.ProbaParamsStage2,
            var totalWeight: Double,
            var scoreUpdate: SrsCalculator.ScoreUpdate?)

    data class PickedQuestion(val item: Item, val probabilityData: SrsCalculator.ProbabilityData, val totalWeight: Double)

    private fun getItem(id: Int): Item =
        itemView.getItem(id)

    val itemView: LearningDbView by lazy {
        getItemView(context, db, testType).apply {
            this.sessionId = this@TestEngine.sessionId
        }
    }

    lateinit var testType: TestType
    lateinit var currentQuestion: Item
        private set
    var currentDebugData: DebugData? = null
        private set
    lateinit var currentAnswers: List<Item>

    var correctCount = 0
        private set
    var questionCount = 0
        private set

    private val history = ArrayList<HistoryLine>()
    private val lastQuestionsIds = ArrayDeque<Int>()
    private var sessionId: Long = 0

    val answerCount
        get() = getAnswerCount(testType)

    fun loadState(savedInstanceState: Bundle) {
        sessionId = savedInstanceState.getLong("sessionId")
        testType = savedInstanceState.getSerializable("testType") as TestType
        currentQuestion = getItem(savedInstanceState.getInt("question"))
        currentAnswers = savedInstanceState.getIntArray("answers")!!.map { getItem(it) }
        correctCount = savedInstanceState.getInt("correctCount")
        questionCount = savedInstanceState.getInt("questionCount")
    }

    fun saveState(outState: Bundle) {
        outState.putLong("sessionId", sessionId)
        outState.putSerializable("testType", testType)
        outState.putInt("question", currentQuestion.id)
        outState.putIntArray("answers", currentAnswers.map { it.id }.toIntArray())
        outState.putInt("correctCount", correctCount)
        outState.putInt("questionCount", questionCount)
    }

    fun prepareNewQuestion() {
        if (sessionId == 0L)
            sessionId = db.initSession(getItemType(testTypes[0]), testTypes)

        testType = testTypes[Random().nextInt(testTypes.size)]

        val (ids, debugParams) = SrsCalculator.fillProbalities(itemView.getEnabledItemsAndScores(), itemView.getMinLastAsked())
        if (ids.size < answerCount) {
            Log.wtf(TAG, "Enabled items ${ids.size} must at least be $answerCount")
            throw RuntimeException("Too few items selected")
        }

        val question = pickQuestion(ids)
        Log.v(TAG, "Selected question: $question")
        currentQuestion = question.item
        currentDebugData = DebugData(question.probabilityData, debugParams.probaParamsStage1, debugParams.probaParamsStage2, question.totalWeight, null)
        currentAnswers = pickAnswers(db, ids, currentQuestion)

        addIdToLastQuestions(currentQuestion.id)
    }

    private fun pickQuestion(ids: List<SrsCalculator.ProbabilityData>): PickedQuestion {
        val idsWithoutRecent = ids.filter { it.itemId !in lastQuestionsIds }

        val totalWeight = idsWithoutRecent.sumOf { it.finalProbability }
        val questionPos = Math.random() * totalWeight
        Log.v(TAG, "Picking a question, questionPos: $questionPos, totalWeight: $totalWeight")
        var question = idsWithoutRecent.last() // take last, it is probably safer with float arithmetic
        run {
            var currentWeight = 0.0
            for (itemData in idsWithoutRecent) {
                currentWeight += itemData.finalProbability
                if (currentWeight >= questionPos) {
                    question = itemData
                    break
                }
            }
            if (currentWeight < questionPos)
                Log.v(TAG, "Couldn't pick a question")
        }

        return PickedQuestion(getItem(question.itemId), question, totalWeight)
    }

    private fun pickAnswers(db: Database, ids: List<SrsCalculator.ProbabilityData>, currentQuestion: Item): List<Item> =
            when (testType) {
                TestType.KANJI_COMPOSITION -> pickCompositionAnswers(db, ids, currentQuestion)
                else -> pickNormalTestAnswers(ids, currentQuestion)
            }

    private fun pickNormalTestAnswers(ids: List<SrsCalculator.ProbabilityData>, currentQuestion: Item): List<Item> {
        val similarItemIds = currentQuestion.similarities.map { it.id }.filter { itemView.isItemEnabled(it) }
        val similarItems =
                if (similarItemIds.size >= answerCount - 1)
                    pickRandom(similarItemIds, answerCount - 1)
                else
                    similarItemIds

        val additionalAnswers = pickRandom(ids.map { it.itemId }, answerCount - 1 - similarItems.size, setOf(currentQuestion.id) + similarItems)

        val currentAnswers = ((additionalAnswers + similarItems).map { getItem(it) } + listOf(currentQuestion)).toMutableList()
        if (currentAnswers.size != answerCount)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of $answerCount")
        currentAnswers.shuffle()

        return currentAnswers
    }

    private fun pickCompositionAnswers(db: Database, ids: List<SrsCalculator.ProbabilityData>, currentQuestion: Item): List<Item> {
        val knowledgeType = getKnowledgeType(testType)

        val currentKanji = currentQuestion.contents as Kanji
        val questionPartsIds = currentKanji.parts.map { it.id }
        val similarPartsIds = db.getSimilarCompositionAnswerIds(currentQuestion.id) - currentQuestion.id
        val otherPartsIds = db.getOtherCompositionAnswerIds(currentQuestion.id) - currentQuestion.id
        val restOfAnswers = ids.map { it.itemId } - currentQuestion.id

        Log.d(TAG, "Parts of ${currentKanji.kanji}: ${questionPartsIds.map { it.asUnicodeCodePoint() }}")
        Log.d(TAG, "Similar parts for ${currentKanji.kanji}: ${similarPartsIds.map { it.asUnicodeCodePoint() }}")
        Log.d(TAG, "Other parts for ${currentKanji.kanji}: ${otherPartsIds.map { it.asUnicodeCodePoint() }}")

        val currentAnswers = sampleCompositionAnswers(listOf(similarPartsIds, otherPartsIds, restOfAnswers), questionPartsIds).map { db.getKanji(it, knowledgeType) }.toMutableList()
        if (currentAnswers.size != answerCount)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of $answerCount")
        currentAnswers.shuffle()

        return currentAnswers
    }

    private fun sampleCompositionAnswers(possibleAnswers: List<List<Int>>, currentAnswers: List<Int>): List<Int> {
        if (possibleAnswers.isEmpty() || currentAnswers.size == answerCount)
            return currentAnswers

        val currentList = possibleAnswers[0] - currentAnswers

        return if (currentList.size <= answerCount - currentAnswers.size) {
            sampleCompositionAnswers(possibleAnswers.drop(1), currentAnswers + currentList)
        } else {
            currentAnswers + pickRandom(currentList, answerCount - currentAnswers.size, setOf())
        }
    }

    private fun addIdToLastQuestions(id: Int) {
        while (lastQuestionsIds.size > LAST_QUESTIONS_TO_AVOID_COUNT - 1)
            lastQuestionsIds.removeFirst()
        lastQuestionsIds.add(id)
    }

    fun markAnswer(certainty: Certainty, wrong: Item? = null) {
        val minLastCorrect = itemView.getMinLastAsked()

        if (certainty == Certainty.DONTKNOW) {
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdate)
            itemView.logTestItem(testType, scoreUpdate, certainty, wrong?.id)
            currentDebugData?.scoreUpdate = scoreUpdate
            if (wrong != null)
                addWrongAnswerToHistory(currentQuestion, wrong)
            else
                addUnknownAnswerToHistory(currentQuestion)
        } else {
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, certainty)
            itemView.applyScoreUpdate(scoreUpdate)
            itemView.logTestItem(testType, scoreUpdate, certainty, wrong?.id)
            currentDebugData?.scoreUpdate = scoreUpdate
            addGoodAnswerToHistory(currentQuestion)
            correctCount += 1
        }

        if (wrong != null) {
            val scoreUpdateBad = SrsCalculator.getScoreUpdate(minLastCorrect, wrong, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdateBad)
        }

        questionCount += 1
    }

    private fun addGoodAnswerToHistory(correct: Item, refresh: Boolean = true) {
        history.add(HistoryLine.Correct(correct.id))
        discardOldHistory()

        goodAnswerCallback(correct, currentDebugData, refresh)
    }

    private fun addWrongAnswerToHistory(correct: Item, wrong: Item, refresh: Boolean = true) {
        history.add(HistoryLine.Incorrect(correct.id, wrong.id))
        discardOldHistory()

        wrongAnswerCallback(correct, currentDebugData, wrong, refresh)
    }

    private fun addUnknownAnswerToHistory(correct: Item, refresh: Boolean = true) {
        history.add(HistoryLine.Unknown(correct.id))
        discardOldHistory()

        unknownAnswerCallback(correct, currentDebugData, refresh)
    }

    private fun discardOldHistory() {
        while (history.size > MAX_HISTORY_SIZE)
            history.removeAt(0)
    }
}

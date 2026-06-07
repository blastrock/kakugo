package org.kaqui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.preference.PreferenceManager
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.LearningDbView
import org.kaqui.model.Word
import org.kaqui.model.TestType
import org.kaqui.model.getAnswerCount
import org.kaqui.model.getItemType
import org.kaqui.model.getKnowledgeType
import org.kaqui.model.similarities
import java.util.ArrayDeque
import java.util.Random

class TestEngine(
        context: Context,
        private val db: Database,
        private val testTypes: List<TestType>,
        private val goodAnswerCallback: (correct: Item, probabilityData: DebugData?) -> Unit,
        private val wrongAnswerCallback: (correct: Item, probabilityData: DebugData?, wrong: Item) -> Unit,
        private val unknownAnswerCallback: (correct: Item, probabilityData: DebugData?) -> Unit) {
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

    // In-memory record of the last marked answer, used to let the user swap it between
    // correct and wrong. Not serialized: surviving background/resume is not a requirement.
    // The Item references retain their pre-answer (baseline) scores because applyScoreUpdate
    // only writes the DB and never mutates the Item.
    data class LastAnswer(
            val correctItem: Item,
            val wrongItem: Item?,
            val originalCorrectUpdate: SrsCalculator.ScoreUpdate,
            val originalWrongUpdate: SrsCalculator.ScoreUpdate?,
            val minLastAsked: Long,
            val answeredAt: Long,
            val originalWasCorrect: Boolean,
            val logRowId: Long,
            val originalCertainty: Certainty,
            val debugData: DebugData?,
            var currentlyCorrect: Boolean,
            // Whether the answered item was already counted as a unique correct item
            // before this answer was applied, so toggling can decide whether removing
            // it from the correct set is safe (only when this answer was its sole source).
            val wasCorrectBeforeThisAnswer: Boolean,
    )

    var lastAnswer: LastAnswer? = null
        private set

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

    // Distinct items asked this session, and the subset currently counted as correct.
    private val askedItemIds = HashSet<Int>()
    private val correctItemIds = HashSet<Int>()

    val uniqueItemCount get() = askedItemIds.size
    val uniqueCorrectCount get() = correctItemIds.size

    private val history = ArrayList<HistoryLine>()
    private val lastQuestionsIds = ArrayDeque<Int>()
    private var sessionId: Long = 0

    val answerCount
        get() = getAnswerCount(testType)

    fun loadState(savedInstanceState: Bundle) {
        sessionId = savedInstanceState.getLong("sessionId")
        testType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getSerializable("testType", TestType::class.java)!!
        } else {
            savedInstanceState.getSerializable("testType") as TestType
        }
        currentQuestion = getItem(savedInstanceState.getInt("question"))
        currentAnswers = savedInstanceState.getIntArray("answers")!!.map { getItem(it) }
        correctCount = savedInstanceState.getInt("correctCount")
        questionCount = savedInstanceState.getInt("questionCount")
        askedItemIds.clear()
        savedInstanceState.getIntArray("askedItemIds")?.let { askedItemIds.addAll(it.toList()) }
        correctItemIds.clear()
        savedInstanceState.getIntArray("correctItemIds")?.let { correctItemIds.addAll(it.toList()) }
        unserializeHistory(savedInstanceState.getByteArray("history")!!)
    }

    fun saveState(outState: Bundle) {
        outState.putLong("sessionId", sessionId)
        outState.putSerializable("testType", testType)
        outState.putInt("question", currentQuestion.id)
        outState.putIntArray("answers", currentAnswers.map { it.id }.toIntArray())
        outState.putInt("correctCount", correctCount)
        outState.putInt("questionCount", questionCount)
        outState.putIntArray("askedItemIds", askedItemIds.toIntArray())
        outState.putIntArray("correctItemIds", correctItemIds.toIntArray())
        outState.putByteArray("history", serializeHistory())
    }

    private fun serializeHistory(): ByteArray {
        val parcel = Parcel.obtain()
        parcel.writeInt(history.size)
        for (line in history)
            when (line) {
                is HistoryLine.Correct -> {
                    parcel.writeByte(0)
                    parcel.writeInt(line.itemId)
                }
                is HistoryLine.Unknown -> {
                    parcel.writeByte(1)
                    parcel.writeInt(line.itemId)
                }
                is HistoryLine.Incorrect -> {
                    parcel.writeByte(2)
                    parcel.writeInt(line.correctItemId)
                    parcel.writeInt(line.answerItemId)
                }
            }
        val data = parcel.marshall()
        parcel.recycle()
        return data
    }

    private fun unserializeHistory(data: ByteArray) {
        val parcel = Parcel.obtain()
        parcel.unmarshall(data, 0, data.size)
        parcel.setDataPosition(0)

        history.clear()

        val count = parcel.readInt()
        repeat(count) { iteration ->
            val type = parcel.readByte()
            when (type.toInt()) {
                0 -> {
                    addGoodAnswerToHistory(getItem(parcel.readInt()))
                }
                1 -> {
                    addUnknownAnswerToHistory(getItem(parcel.readInt()))
                }
                2 -> {
                    addWrongAnswerToHistory(getItem(parcel.readInt()), getItem(parcel.readInt()))
                }
            }
        }

        parcel.recycle()
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

        val usedIds = setOf(currentQuestion.id) + similarItems
        var remaining = answerCount - usedIds.size

        val word = currentQuestion.contents as? Word
        val exprItems =
                if (remaining > 0 && word != null) {
                    val exprCandidates = db.getEnabledWordIdsByExpr(word.expr, usedIds)
                    if (exprCandidates.size >= remaining)
                        pickRandom(exprCandidates, remaining)
                    else
                        exprCandidates
                } else
                    listOf()
        remaining -= exprItems.size

        val additionalAnswers = pickRandom(ids.map { it.itemId }, remaining, usedIds + exprItems)

        val currentAnswers = ((additionalAnswers + exprItems + similarItems).map { getItem(it) } + listOf(currentQuestion)).toMutableList()
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
        val answeredItem = currentQuestion

        val scoreUpdate: SrsCalculator.ScoreUpdate
        val logRowId: Long
        if (certainty == Certainty.DONTKNOW) {
            scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, answeredItem, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdate)
            logRowId = itemView.logTestItem(testType, scoreUpdate, certainty, wrong?.id)
            currentDebugData?.scoreUpdate = scoreUpdate
            if (wrong != null)
                addWrongAnswerToHistory(answeredItem, wrong)
            else
                addUnknownAnswerToHistory(answeredItem)
        } else {
            scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, answeredItem, certainty)
            itemView.applyScoreUpdate(scoreUpdate)
            logRowId = itemView.logTestItem(testType, scoreUpdate, certainty, wrong?.id)
            currentDebugData?.scoreUpdate = scoreUpdate
            addGoodAnswerToHistory(answeredItem)
            correctCount += 1
        }

        var scoreUpdateBad: SrsCalculator.ScoreUpdate? = null
        if (wrong != null) {
            scoreUpdateBad = SrsCalculator.getScoreUpdate(minLastCorrect, wrong, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdateBad)
        }

        val wasCorrect = certainty != Certainty.DONTKNOW && wrong == null

        val wasCorrectBefore = answeredItem.id in correctItemIds
        askedItemIds.add(answeredItem.id)
        if (wasCorrect)
            correctItemIds.add(answeredItem.id)

        lastAnswer = LastAnswer(
                correctItem = answeredItem,
                wrongItem = wrong,
                originalCorrectUpdate = scoreUpdate,
                originalWrongUpdate = scoreUpdateBad,
                minLastAsked = minLastCorrect,
                answeredAt = scoreUpdate.lastAsked,
                originalWasCorrect = wasCorrect,
                logRowId = logRowId,
                originalCertainty = certainty,
                debugData = currentDebugData,
                currentlyCorrect = wasCorrect,
                wasCorrectBeforeThisAnswer = wasCorrectBefore,
        )

        questionCount += 1
    }

    // Swap the last answer between correct and wrong, reverting and re-applying scores
    // accordingly. Returns the updated LastAnswer, or null if there is no last answer.
    fun toggleLastAnswer(): LastAnswer? {
        val la = lastAnswer ?: return null
        la.currentlyCorrect = !la.currentlyCorrect
        val correctScoreUpdate: SrsCalculator.ScoreUpdate
        if (la.currentlyCorrect == la.originalWasCorrect) {
            // Back to the original side: re-apply the exact original updates and log row.
            correctScoreUpdate = la.originalCorrectUpdate
            itemView.applyScoreUpdate(correctScoreUpdate)
            la.originalWrongUpdate?.let { itemView.applyScoreUpdate(it) }
            itemView.updateTestItem(la.logRowId, la.originalCertainty, la.wrongItem?.id)
        } else if (la.currentlyCorrect) {
            // wrong -> correct: reward short score only (SURE), leave long at baseline.
            val sure = SrsCalculator.getScoreUpdate(la.minLastAsked, la.correctItem, Certainty.SURE)
            correctScoreUpdate = sure.copy(longScore = la.correctItem.longScore.toFloat(), lastAsked = la.answeredAt)
            itemView.applyScoreUpdate(correctScoreUpdate)
            // Cancel the penalty on the picked wrong item by restoring its baseline scores.
            la.wrongItem?.let { w ->
                itemView.applyScoreUpdate(SrsCalculator.ScoreUpdate(
                        w.id, w.shortScore.toFloat(), w.longScore.toFloat(), w.lastAsked, la.minLastAsked))
            }
            itemView.updateTestItem(la.logRowId, Certainty.SURE, null)
        } else {
            // correct -> wrong: revert the increase and apply the MAYBE penalty
            val dk = SrsCalculator.getScoreUpdate(la.minLastAsked, la.correctItem, Certainty.MAYBE)
            correctScoreUpdate = dk.copy(lastAsked = la.answeredAt)
            itemView.applyScoreUpdate(correctScoreUpdate)
            itemView.updateTestItem(la.logRowId, Certainty.MAYBE, null)
        }
        // Keep the debug overlay in sync with the swapped state.
        la.debugData?.scoreUpdate = correctScoreUpdate
        correctCount += if (la.currentlyCorrect) 1 else -1

        // Keep the unique-correct set in sync. Toggling to correct always counts the
        // item; toggling to wrong only uncounts it when this answer was its sole source
        // of correctness (an earlier committed question keeps it sticky).
        if (la.currentlyCorrect)
            correctItemIds.add(la.correctItem.id)
        else if (!la.wasCorrectBeforeThisAnswer)
            correctItemIds.remove(la.correctItem.id)

        return la
    }

    private fun addGoodAnswerToHistory(correct: Item) {
        history.add(HistoryLine.Correct(correct.id))
        discardOldHistory()

        goodAnswerCallback(correct, currentDebugData)
    }

    private fun addWrongAnswerToHistory(correct: Item, wrong: Item) {
        history.add(HistoryLine.Incorrect(correct.id, wrong.id))
        discardOldHistory()

        wrongAnswerCallback(correct, currentDebugData, wrong)
    }

    private fun addUnknownAnswerToHistory(correct: Item) {
        history.add(HistoryLine.Unknown(correct.id))
        discardOldHistory()

        unknownAnswerCallback(correct, currentDebugData)
    }

    private fun discardOldHistory() {
        while (history.size > MAX_HISTORY_SIZE)
            history.removeAt(0)
    }
}

package org.kaqui

import android.os.Bundle
import android.os.Parcel
import android.util.Log
import org.kaqui.model.*
import java.util.*

class QuizzEngine(
        private val db: KaquiDb,
        private val quizzType: QuizzType,
        private val goodAnswerCallback: (correct: Item, probabilityData: QuizzEngine.DebugData?) -> Unit,
        private val wrongAnswerCallback: (correct: Item, probabilityData: QuizzEngine.DebugData?, wrong: Item) -> Unit,
        private val unknownAnswerCallback: (correct: Item, probabilityData: QuizzEngine.DebugData?) -> Unit) {
    private sealed class HistoryLine {
        data class Correct(val itemId: Int) : HistoryLine()
        data class Unknown(val itemId: Int) : HistoryLine()
        data class Incorrect(val correctItemId: Int, val answerItemId: Int) : HistoryLine()
    }

    companion object {
        private const val TAG = "QuizzEngine"
        const val NB_ANSWERS = 6
        private const val LAST_QUESTIONS_TO_AVOID_COUNT = 6
        const val MAX_HISTORY_SIZE = 40

        fun getItemView(db: KaquiDb, quizzType: QuizzType): LearningDbView =
                when (quizzType) {
                    QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA -> db.hiraganaView
                    QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA -> db.katakanaView

                    QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI, QuizzType.KANJI_WRITING -> db.kanjiView

                    QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING, QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> db.wordView
                }
    }

    data class DebugData(
            var probabilityData: SrsCalculator.ProbabilityData,
            var probaParamsStage1: SrsCalculator.ProbaParamsStage1,
            var probaParamsStage2: SrsCalculator.ProbaParamsStage2,
            var totalWeight: Double,
            var scoreUpdate: SrsCalculator.ScoreUpdate?)

    data class PickedQuestion(val item: Item, val probabilityData: SrsCalculator.ProbabilityData, val totalWeight: Double)

    lateinit var currentQuestion: Item
        private set
    var currentDebugData: DebugData? = null
        private set
    lateinit var currentAnswers: List<Item>
        private set

    var correctCount = 0
        private set
    var questionCount = 0
        private set

    private val history = ArrayList<HistoryLine>()
    private val lastQuestionsIds = ArrayDeque<Int>()

    fun loadState(savedInstanceState: Bundle) {
        currentQuestion = getItem(db, savedInstanceState.getInt("question"))
        currentAnswers = savedInstanceState.getIntArray("answers").map { getItem(db, it) }
        correctCount = savedInstanceState.getInt("correctCount")
        questionCount = savedInstanceState.getInt("questionCount")
        unserializeHistory(savedInstanceState.getByteArray("history"))
    }

    fun saveState(outState: Bundle) {
        outState.putInt("question", currentQuestion.id)
        outState.putIntArray("answers", currentAnswers.map { it.id }.toIntArray())
        outState.putInt("correctCount", correctCount)
        outState.putInt("questionCount", questionCount)
        outState.putByteArray("history", serializeHistory())
    }

    private fun <T> pickRandom(list: List<T>, sample: Int, avoid: Set<T> = setOf()): List<T> {
        if (sample > list.size - avoid.size)
            throw RuntimeException("can't get a sample of size $sample on list of size ${list.size - avoid.size}")

        val chosen = mutableSetOf<T>()
        while (chosen.size < sample) {
            val r = list[(Math.random() * list.size).toInt()]
            if (r !in avoid)
                chosen.add(r)
        }
        return chosen.toList()
    }

    fun prepareNewQuestion() {
        val (ids, debugParams) = SrsCalculator.fillProbalities(itemView.getEnabledItemsAndScores(), itemView.getLastCorrectFirstDecile())
        if (ids.size < NB_ANSWERS) {
            Log.wtf(TAG, "Too few items selected for a quizz: ${ids.size}")
            return
        }

        val question = pickQuestion(db, ids)
        Log.v(TAG, "Selected question: $question")
        currentQuestion = question.item
        currentDebugData = DebugData(question.probabilityData, debugParams.probaParamsStage1, debugParams.probaParamsStage2, question.totalWeight, null)
        currentAnswers = pickAnswers(db, ids, currentQuestion)

        addIdToLastQuestions(currentQuestion.id)
    }

    private fun pickQuestion(db: KaquiDb, ids: List<SrsCalculator.ProbabilityData>): PickedQuestion {
        val idsWithoutRecent = ids.filter { it.itemId !in lastQuestionsIds }

        val totalWeight = idsWithoutRecent.map { it.finalProbability }.sum()
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

        return PickedQuestion(getItem(db, question.itemId), question, totalWeight)
    }

    private fun pickAnswers(db: KaquiDb, ids: List<SrsCalculator.ProbabilityData>, currentQuestion: Item): List<Item> {
        val similarItemIds = currentQuestion.similarities.map { it.id }.filter { itemView.isItemEnabled(it) }
        val similarItems =
                if (similarItemIds.size >= NB_ANSWERS - 1)
                    pickRandom(similarItemIds, NB_ANSWERS - 1)
                else
                    similarItemIds

        val additionalAnswers = pickRandom(ids.map { it.itemId }, NB_ANSWERS - 1 - similarItems.size, setOf(currentQuestion.id) + similarItems)

        val currentAnswers = ((additionalAnswers + similarItems).map { getItem(db, it) } + listOf(currentQuestion)).toMutableList()
        if (currentAnswers.size != NB_ANSWERS)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of $NB_ANSWERS")
        currentAnswers.shuffle()

        return currentAnswers
    }

    private fun addIdToLastQuestions(id: Int) {
        while (lastQuestionsIds.size > LAST_QUESTIONS_TO_AVOID_COUNT - 1)
            lastQuestionsIds.removeFirst()
        lastQuestionsIds.add(id)
    }

    fun selectAnswer(certainty: Certainty, position: Int) {
        val minLastCorrect = itemView.getLastCorrectFirstDecile()

        if (certainty == Certainty.DONTKNOW) {
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdate)
            currentDebugData?.scoreUpdate = scoreUpdate
            addUnknownAnswerToHistory(currentQuestion)
        } else if (currentAnswers[position] == currentQuestion ||
                // also compare answer texts because different answers can have the same readings
                // like 副 and 福 and we don't want to penalize the user for that
                currentAnswers[position].getAnswerText(quizzType) == currentQuestion.getAnswerText(quizzType) ||
                // same for question text
                currentAnswers[position].getQuestionText(quizzType) == currentQuestion.getQuestionText(quizzType)) {
            // correct
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, certainty)
            itemView.applyScoreUpdate(scoreUpdate)
            currentDebugData?.scoreUpdate = scoreUpdate
            addGoodAnswerToHistory(currentQuestion)
            correctCount += 1
        } else {
            // wrong
            val scoreUpdateGood = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdateGood)
            val scoreUpdateBad = SrsCalculator.getScoreUpdate(minLastCorrect, currentAnswers[position], Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdateBad)
            currentDebugData?.scoreUpdate = scoreUpdateGood
            addWrongAnswerToHistory(currentQuestion, currentAnswers[position])
        }

        questionCount += 1
    }

    fun markAnswer(certainty: Certainty) {
        val minLastCorrect = itemView.getLastCorrectFirstDecile()

        if (certainty == Certainty.DONTKNOW) {
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, Certainty.DONTKNOW)
            itemView.applyScoreUpdate(scoreUpdate)
            currentDebugData?.scoreUpdate = scoreUpdate
            addUnknownAnswerToHistory(currentQuestion)
        } else {
            val scoreUpdate = SrsCalculator.getScoreUpdate(minLastCorrect, currentQuestion, certainty)
            itemView.applyScoreUpdate(scoreUpdate)
            currentDebugData?.scoreUpdate = scoreUpdate
            addGoodAnswerToHistory(currentQuestion)
            correctCount += 1
        }

        questionCount += 1
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
        repeat(count) {
            val type = parcel.readByte()
            when (type.toInt()) {
                0 -> {
                    addGoodAnswerToHistory(getItem(db, parcel.readInt()))
                }
                1 -> {
                    addUnknownAnswerToHistory(getItem(db, parcel.readInt()))
                }
                2 -> {
                    addWrongAnswerToHistory(getItem(db, parcel.readInt()), getItem(db, parcel.readInt()))
                }
            }
        }

        parcel.recycle()
    }

    private fun getItem(db: KaquiDb, id: Int): Item =
            itemView.getItem(id)

    private val itemView: LearningDbView
        get() = getItemView(db, quizzType)
}

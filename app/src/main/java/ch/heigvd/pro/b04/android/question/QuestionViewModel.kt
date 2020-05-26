package ch.heigvd.pro.b04.android.question

import android.app.Application
import androidx.lifecycle.viewModelScope
import ch.heigvd.pro.b04.android.datamodel.Answer
import ch.heigvd.pro.b04.android.datamodel.Question
import ch.heigvd.pro.b04.android.network.*
import ch.heigvd.pro.b04.android.network.RockinAPI.Companion.voteForAnswerSuspending
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class QuestionViewModel(application: Application, question : Question, private val token : String)
        : RequestsViewModel(application, question.idModerator.toInt(), question.idPoll.toInt(), token) {

    private val nextQuestionToShow : MutableStateFlow<Question> = MutableStateFlow(question)
    private val previousQuestionInPoll : MutableStateFlow<Question?> = MutableStateFlow(null)
    private val nextQuestionInPoll : MutableStateFlow<Question?> = MutableStateFlow(null)
    private val nbCheckedAnswer : MutableStateFlow<Int> = MutableStateFlow(0)
    private val notifyMaxAnswer : MutableStateFlow<Int> = MutableStateFlow(0)
    private val networkErrors : Flow<NetworkError>

    private var lastVoteAtTime : Long = System.currentTimeMillis()

    val currentQuestion : MutableStateFlow<Question> = MutableStateFlow(question)
    val answers: Flow<List<Answer>>

    init {
        val pollingTimeToAnswers = currentQuestion
            .map { System.currentTimeMillis() to RockinAPI.getAnswersSuspending(it, token) }

        val pollingAnswerDelayed = pollingTimeToAnswers
            .filter { it.first > lastVoteAtTime + REFRESH_DELAY }
            .map { it.second }

        val answersUpdate : Flow<Response<List<Answer>>> = currentQuestion
            .filterNotNull()
            .map { RockinAPI.getAnswersSuspending(it, token) }
            .catch {}
            .filterNotNull()

        val requestAnswers = merge(pollingAnswerDelayed, answersUpdate)

        answers = requestAnswers
            .keepBody()
            .onEach { it.sortedBy { q -> q.idAnswer } }
        networkErrors = merge(requestAnswers.keepError(), super.networkErrors())

        val currentToAllQuestions : Flow<Pair<Question, List<Question>>> = currentQuestion
            .filterNotNull()
            .zip(questions) { x, y -> x to y }

        viewModelScope.launch {
            questions.map {
                it.filter { it.idQuestion == nextQuestionToShow.value.idQuestion }.get(0)
            }.collect { currentQuestion.value = it }
        }

        viewModelScope.launch {
            answers.map {
                it.filter { it.isChecked }.size
            }.collect { nbCheckedAnswer.value = it }
        }

        viewModelScope.launch {
            currentToAllQuestions.map { (current, all) ->
                var candidate: Question? = null
                var candidateIndex = Double.MIN_VALUE

                all.forEach {
                    val newIndex = it.indexInPoll
                    if (newIndex < current.indexInPoll && newIndex > candidateIndex) {
                        candidate = it
                        candidateIndex = newIndex
                    }
                }

                return@map candidate
            }.collect {
                previousQuestionInPoll.value = it
            }
        }

        viewModelScope.launch {
            currentToAllQuestions.map { (current, all) ->
                var candidate: Question? = null
                var candidateIndex = Double.MAX_VALUE

                all.forEach {
                    val newIndex = it.indexInPoll
                    if (newIndex > current.indexInPoll && newIndex < candidateIndex) {
                        candidate = it
                        candidateIndex = newIndex
                    }
                }

                return@map candidate
            }.collect {
                nextQuestionInPoll.value = it
            }
        }
    }

    fun selectAnswer(answer: Answer) {
        val question: Question? = currentQuestion.value

        if (question == null || question.idQuestion != answer.idQuestion)
            return

        val max = if (question.answerMax < question.answerMin) 0 else question.answerMax

        if (answer.isChecked || max > nbCheckedAnswer.value || max == 0) {
            lastVoteAtTime = System.currentTimeMillis()

            if (answer.isChecked) nbCheckedAnswer.value-- else nbCheckedAnswer.value++

            answer.toggle()
            // Note that for now, we do not take the result into account
            viewModelScope.launch {
                voteForAnswerSuspending(answer, token)
            }
        } else if (max != 0 && max == nbCheckedAnswer.value) {
            notifyMaxAnswer.value = question.answerMax

            // Not useless: if we do not set the value back to 0, the activity will not be
            // notified if the user clicks multiple times on an answer
            notifyMaxAnswer.value = 0
        }
    }

    fun changeToPreviousQuestion() : Unit {
        if (previousQuestionInPoll.value != null)
            nextQuestionToShow.value = previousQuestionInPoll.value!!
    }

    fun changeToNextQuestion() : Unit {
        if (nextQuestionInPoll.value != null)
            nextQuestionToShow.value = nextQuestionInPoll.value!!
    }

    fun getNbCheckedAnswer() : StateFlow<Int> {
        return nbCheckedAnswer
    }

    fun notifyMaxAnswers() : StateFlow<Int> {
        return notifyMaxAnswer
    }

    override fun networkErrors(): Flow<NetworkError> {
        return networkErrors
    }

    companion object {
        private const val REFRESH_DELAY :Long = 5000
    }
}
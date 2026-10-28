package app.jlpt

import android.app.Application
import app.jlpt.data.ClaudeClient
import app.jlpt.data.Content
import app.jlpt.data.Prefs
import app.jlpt.data.QuestionMaker
import app.jlpt.data.Reminder
import app.jlpt.data.Speech
import app.jlpt.data.Study
import app.jlpt.data.StudyDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JlptApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var prefs: Prefs; private set
    lateinit var db: StudyDb; private set
    lateinit var content: Content; private set
    lateinit var study: Study; private set
    lateinit var questions: QuestionMaker; private set
    lateinit var claude: ClaudeClient; private set
    val speech: Speech by lazy { Speech(this, prefs) }

    override fun onCreate() {
        super.onCreate()
        // If the app ever closes by itself, keep what went wrong so Settings can show it.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                getSharedPreferences("crash", MODE_PRIVATE).edit()
                    .putString("last", "${java.time.LocalDateTime.now()}\n${android.util.Log.getStackTraceString(e).take(4000)}")
                    .commit()
            }
            previous?.uncaughtException(thread, e)
        }
        prefs = Prefs(this)
        db = StudyDb.open(this)
        content = Content(this)
        study = Study(content, db.dao(), prefs)
        questions = QuestionMaker(content)
        claude = ClaudeClient(prefs)
        Reminder.channel(this)
        Reminder.schedule(this, prefs)
        scope.launch(Dispatchers.IO) {
            content.warmUp()
            study.forgiveBrokenQuestions()
            study.mergeKatakanaDuplicates()
            study.mergeRepeats()
            study.load()
        }
    }
}

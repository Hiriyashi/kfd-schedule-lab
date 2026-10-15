package schedule

import java.time.LocalDate
import java.util.Collections

internal fun String.isClean(): Boolean =
    isNotEmpty() && !first().isWhitespace() && !last().isWhitespace() && none { it.isISOControl() }

sealed interface Session {
    val date: LocalDate
    val topic: String
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = Collections.unmodifiableList(teachers.toList())
    init { validateActive(date, topic, location, this.teachers, minTeachers = 1) }
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = Collections.unmodifiableList(teachers.toList())
    init { validateActive(date, topic, location, this.teachers, minTeachers = 1) }
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = Collections.unmodifiableList(teachers.toList())
    init { validateActive(date, topic, location, this.teachers, minTeachers = 2) }
}

class Cancelled(
    override val date: LocalDate,
    override val topic: String,
) : Session {
    init { validateCommon(date, topic) }
}

private fun validateCommon(date: LocalDate, topic: String) {
    require(date.year in 1..9999) { "Year must be in 1..9999" }
    require(topic.isClean()) { "Invalid topic" }
}

private fun validateActive(
    date: LocalDate,
    topic: String,
    location: String,
    teachers: List<String>,
    minTeachers: Int,
) {
    validateCommon(date, topic)
    require(location.isClean() && location != "-") { "Invalid location" }
    require(teachers.size >= minTeachers) { "At least $minTeachers teacher(s) required" }
    require(teachers.all { it.isClean() && it != "-" }) { "Invalid teacher name" }
    require(teachers.distinct().size == teachers.size) { "Duplicate teachers" }
}

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

class InMemorySessionRepository : SessionRepository {
    private val items = mutableListOf<Session>()
    override fun add(session: Session) { items.add(session) }
    override fun addAll(sessions: List<Session>) { items.addAll(sessions.toList()) }
    override fun all(): List<Session> = Collections.unmodifiableList(items.toList())
}

data class AuditEvent(val operation: String, val count: Int)

class AuditedSessionRepository(
    private val delegate: SessionRepository = InMemorySessionRepository(),
) : SessionRepository by delegate {
    private val _events = mutableListOf<AuditEvent>()

    var addedCount: Int = 0
        private set

    val auditEvents: List<AuditEvent>
        get() = Collections.unmodifiableList(_events.toList())

    override fun add(session: Session) {
        delegate.add(session)
        addedCount += 1
        _events.add(AuditEvent("add", 1))
    }

    override fun addAll(sessions: List<Session>) {
        val snapshot = sessions.toList()
        delegate.addAll(snapshot)
        addedCount += snapshot.size
        _events.add(AuditEvent("addAll", snapshot.size))
    }
}

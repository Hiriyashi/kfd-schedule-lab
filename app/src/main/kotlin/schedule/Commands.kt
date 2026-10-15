package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

private val DATE_REGEX = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val FIELD_NAMES = listOf("date", "type", "topic", "location", "teachers")

sealed interface ParseResult {
    data class Success(val session: Session) : ParseResult
    data class Failure(val lineNumber: Long, val reason: RejectionReason) : ParseResult
}

sealed interface RejectionReason {
    data object InvalidFieldSeparator : RejectionReason
    data class UnexpectedFieldCount(val count: Int) : RejectionReason
    data class EmptyRequiredField(val field: String) : RejectionReason
    data class InvalidDate(val value: String) : RejectionReason
    data class UnknownSessionType(val value: String) : RejectionReason
    data class NotEnoughExamTeachers(val count: Int) : RejectionReason
    data class ControlCharacter(val field: String, val value: String) : RejectionReason
    data class ExternalWhitespace(val field: String, val value: String) : RejectionReason
    data class InvalidCancelledField(val field: String, val value: String) : RejectionReason
    data class InvalidLocation(val value: String) : RejectionReason
    data class InvalidTeacherName(val value: String) : RejectionReason
    data class DuplicateTeacher(val value: String) : RejectionReason
}

fun parseScheduleLine(lineNumber: Long, line: String): ParseResult {
    fun fail(reason: RejectionReason) = ParseResult.Failure(lineNumber, reason)

    if ('|' !in line) return fail(RejectionReason.InvalidFieldSeparator)
    val fields = line.split('|')
    if (fields.size != 5) return fail(RejectionReason.UnexpectedFieldCount(fields.size))
    val blankIndex = fields.indexOfFirst { it.isBlank() }
    if (blankIndex != -1) return fail(RejectionReason.EmptyRequiredField(FIELD_NAMES[blankIndex]))
    val (dateStr, typeStr, topic, location, teachersStr) = fields
    val date = parseDate(dateStr) ?: return fail(RejectionReason.InvalidDate(dateStr))
    val type = SessionType.entries.find { it.name == typeStr }
        ?: return fail(RejectionReason.UnknownSessionType(typeStr))
    val teachers = teachersStr.split(',')
    if (type == SessionType.EXAM) {
        val count = teachers.count { it.isNotBlank() && it != "-" }
        if (count < 2) return fail(RejectionReason.NotEnoughExamTeachers(count))
    }
    for (i in 0..3) {
        if (fields[i].any { it.isISOControl() }) {
            return fail(RejectionReason.ControlCharacter(FIELD_NAMES[i], fields[i]))
        }
    }
    if (type == SessionType.CANCELLED) {
        if (teachersStr.any { it.isISOControl() }) {
            return fail(RejectionReason.ControlCharacter("teachers", teachersStr))
        }
    } else {
        teachers.firstOrNull { it.any(Char::isISOControl) }?.let {
            return fail(RejectionReason.ControlCharacter("teachers", it))
        }
    }
    for (i in 0..3) {
        if (fields[i].first().isWhitespace() || fields[i].last().isWhitespace()) {
            return fail(RejectionReason.ExternalWhitespace(FIELD_NAMES[i], fields[i]))
        }
    }
    if (type == SessionType.CANCELLED) {
        if (teachersStr.first().isWhitespace() || teachersStr.last().isWhitespace()) {
            return fail(RejectionReason.ExternalWhitespace("teachers", teachersStr))
        }
    } else {
        teachers.firstOrNull { it.isNotBlank() && (it.first().isWhitespace() || it.last().isWhitespace()) }?.let {
            return fail(RejectionReason.ExternalWhitespace("teachers", it))
        }
    }
    if (type == SessionType.CANCELLED) {
        if (location != "-") return fail(RejectionReason.InvalidCancelledField("location", location))
        if (teachersStr != "-") return fail(RejectionReason.InvalidCancelledField("teachers", teachersStr))
        return ParseResult.Success(Cancelled(date, topic))
    }
    if (location == "-") return fail(RejectionReason.InvalidLocation(location))
    teachers.firstOrNull { !it.isClean() || it == "-" }?.let {
        return fail(RejectionReason.InvalidTeacherName(it))
    }
    val seen = mutableSetOf<String>()
    teachers.firstOrNull { !seen.add(it) }?.let {
        return fail(RejectionReason.DuplicateTeacher(it))
    }
    val session = when (type) {
        SessionType.LECTURE -> Lecture(date, topic, location, teachers)
        SessionType.PRACTICE -> Practice(date, topic, location, teachers)
        SessionType.EXAM -> Exam(date, topic, location, teachers)
        SessionType.CANCELLED -> Cancelled(date, topic)
    }
    return ParseResult.Success(session)
}

private fun parseDate(s: String): LocalDate? {
    if (!DATE_REGEX.matches(s)) return null
    val d = try { LocalDate.parse(s) } catch (_: DateTimeParseException) { return null }
    return if (d.year in 1..9999) d else null
}

private fun escape(s: String): String = buildString {
    for (c in s) {
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c.isISOControl() || c.code > 0x7E -> append("\\u%04X".format(c.code))
            else -> append(c)
        }
    }
}

fun formatRejection(failure: ParseResult.Failure): String {
    val n = failure.lineNumber
    return when (val r = failure.reason) {
        RejectionReason.InvalidFieldSeparator -> "line $n: invalid field separator"
        is RejectionReason.UnexpectedFieldCount -> "line $n: got ${r.count} fields, expected 5"
        is RejectionReason.EmptyRequiredField -> "line $n: empty required field \"${r.field}\""
        is RejectionReason.InvalidDate -> "line $n: invalid date \"${escape(r.value)}\""
        is RejectionReason.UnknownSessionType -> "line $n: unknown session type \"${escape(r.value)}\""
        is RejectionReason.NotEnoughExamTeachers -> "line $n: type \"EXAM\" requires at least 2 teachers, got ${r.count}"
        is RejectionReason.ControlCharacter -> "line $n: control character in field \"${r.field}\": \"${escape(r.value)}\""
        is RejectionReason.ExternalWhitespace -> "line $n: external whitespace in field \"${r.field}\": \"${escape(r.value)}\""
        is RejectionReason.InvalidCancelledField -> "line $n: invalid cancelled field \"${r.field}\": \"${escape(r.value)}\""
        is RejectionReason.InvalidLocation -> "line $n: invalid location \"${escape(r.value)}\""
        is RejectionReason.InvalidTeacherName -> "line $n: invalid teacher \"${escape(r.value)}\""
        is RejectionReason.DuplicateTeacher -> "line $n: duplicate teacher \"${escape(r.value)}\""
    }
}

private val Session.type: SessionType
    get() = when (this) {
        is Lecture -> SessionType.LECTURE
        is Practice -> SessionType.PRACTICE
        is Exam -> SessionType.EXAM
        is Cancelled -> SessionType.CANCELLED
    }

private val Session.locationText: String
    get() = when (this) {
        is Lecture -> location
        is Practice -> location
        is Exam -> location
        is Cancelled -> "-"
    }

private val Session.teachersText: String
    get() = when (this) {
        is Lecture -> teachers.joinToString(", ")
        is Practice -> teachers.joinToString(", ")
        is Exam -> teachers.joinToString(", ")
        is Cancelled -> "-"
    }

fun runPeriod(command: Command.Period): Int {
    var total = 0L
    var hasErrors = false
    forEachScheduleLine(command.file) { num, line ->
        when (val res = parseScheduleLine(num, line)) {
            is ParseResult.Success -> if (res.session.date in command.from..command.to) {
                val s = res.session
                println("${s.date} | ${s.type} | ${s.topic} | ${s.locationText}")
                total++
            }
            is ParseResult.Failure -> hasErrors = true
        }
    }
    println("Total: $total")
    return if (hasErrors) 1 else 0
}

fun runType(command: Command.ByType): Int {
    var total = 0L
    var hasErrors = false
    forEachScheduleLine(command.file) { num, line ->
        when (val res = parseScheduleLine(num, line)) {
            is ParseResult.Success -> if (res.session.type == command.type) {
                val s = res.session
                println("${s.date} | ${s.type} | ${s.topic} | ${s.locationText} | ${s.teachersText}")
                total++
            }
            is ParseResult.Failure -> hasErrors = true
        }
    }
    println("Total: $total")
    return if (hasErrors) 1 else 0
}

fun runErrors(command: Command.Errors): Int {
    var totalLines = 0L
    var rejected = 0L
    forEachScheduleLine(command.file) { num, line ->
        totalLines++
        val res = parseScheduleLine(num, line)
        if (res is ParseResult.Failure) {
            rejected++
            println(formatRejection(res))
        }
    }
    println("Rejected: $rejected of $totalLines")
    return if (rejected > 0) 1 else 0
}

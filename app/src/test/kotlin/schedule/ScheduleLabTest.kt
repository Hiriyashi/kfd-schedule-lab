package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ScheduleLabTest {
    private val date = LocalDate.of(2026, 10, 5)

    @Test
    fun `valid creation of all four types`() {
        val l = Lecture(date, "Topic", "Room 1", listOf("Ivanov"))
        val p = Practice(date, "Topic", "Room 1", listOf("Petrov"))
        val e = Exam(date, "Topic", "Room 1", listOf("Ivanov", "ivanov"))
        val c = Cancelled(date, "Topic")
        assertEquals(listOf("Ivanov", "ivanov"), e.teachers)
        assertEquals("Topic", l.topic)
        assertEquals("Room 1", p.location)
        assertEquals(date, c.date)
    }

    @Test
    fun `breaks invariants 1 to 5 throw IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Cancelled(date, "") }
        assertFailsWith<IllegalArgumentException> { Cancelled(date, "   ") }
        assertFailsWith<IllegalArgumentException> { Cancelled(LocalDate.of(0, 1, 1), "Topic") }
        assertFailsWith<IllegalArgumentException> { Cancelled(LocalDate.of(10000, 1, 1), "Topic") }
        assertFailsWith<IllegalArgumentException> { Lecture(date, "Topic", "Room 1", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room 1", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room 1", listOf("Ivanov", "Ivanov")) }
    }

    @Test
    fun `6 and 7 - defensive copies protect session teachers`() {
        val names = mutableListOf("Ivanov", "Petrov")
        val exam = Exam(date, "Topic", "Room 1", names)
        names.clear()
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)

        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (exam.teachers as MutableList<String>).clear()
        }
    }

    @Test
    fun `repository snapshots and audit delegation`() {
        val repo = AuditedSessionRepository()
        val s1 = Cancelled(date, "T1")
        val s2 = Cancelled(date, "T2")

        repo.add(s1)
        val snap = repo.all()
        repo.addAll(listOf(s2))
        repo.addAll(emptyList())

        assertEquals(1, snap.size)
        assertEquals(2, repo.all().size)
        assertEquals(2, repo.addedCount)
        assertEquals(
            listOf(AuditEvent("add", 1), AuditEvent("addAll", 1), AuditEvent("addAll", 0)),
            repo.auditEvents,
        )
    }

    @Test
    fun `parser priorities and escaping`() {
        val r1 = parseScheduleLine(1, "2026-13-45|SEMINAR|Topic|Room 1|Ivanov")
        assertIs<ParseResult.Failure>(r1)
        assertEquals("line 1: invalid date \"2026-13-45\"", formatRejection(r1))

        val r2 = parseScheduleLine(2, "2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov")
        assertIs<ParseResult.Failure>(r2)
        assertEquals("line 2: duplicate teacher \"Ivanov\"", formatRejection(r2))

        val r3 = parseScheduleLine(3, "2026-10-05|A\"B\\C\tЯ|Topic|Room 1|Ivanov")
        assertIs<ParseResult.Failure>(r3)
        assertEquals("line 3: unknown session type \"A\\\"B\\\\C\\u0009\\u042F\"", formatRejection(r3))
    }
}

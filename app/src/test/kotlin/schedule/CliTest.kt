package schedule

import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CliTest {
    @Test
    fun parsesAllCommandsAndOptionOrders() {
        assertEquals(
            Command.Period("samples/clean.txt", LocalDate.of(2026, 9, 28), LocalDate.of(2027, 5, 13)),
            parseCommand(arrayOf("period", "--to", "2027-05-13", "--file", "samples/clean.txt", "--from", "2026-09-28")),
        )
        for (type in SessionType.entries) {
            assertEquals(
                Command.ByType("a file.txt", type),
                parseCommand(arrayOf("type", "--type", type.name, "--file", "a file.txt")),
            )
        }
        assertEquals(Command.Errors("./--data"), parseCommand(arrayOf("errors", "--file", "./--data")))
        assertEquals(
            Command.Period("x", LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)),
            parseCommand(arrayOf("period", "--file", "x", "--from", "0001-01-01", "--to", "9999-12-31")),
        )
    }

    @Test
    fun rejectsInvalidArgumentsBeforeCallingAHandler() {
        val cases = listOf(
            emptyArray(),
            arrayOf("other"),
            arrayOf("errors"),
            arrayOf("errors", "--file"),
            arrayOf("errors", "--file", ""),
            arrayOf("errors", "--file", " "),
            arrayOf("errors", "--file", "--data"),
            arrayOf("errors", "--file=x"),
            arrayOf("errors", "--unknown", "x"),
            arrayOf("errors", "--file", "x", "--extra", "y"),
            arrayOf("type", "--file", "x", "--file", "y"),
            arrayOf("type", "--type", "exam", "--file", "x"),
            arrayOf("type", "--type", "SEMINAR", "--file", "x"),
        ) + listOf("0000-01-01", "10000-01-01", "2026-02-29", "2026-13-01", "2026-1-01", " 2026-01-01")
            .map { arrayOf("period", "--file", "x", "--from", it, "--to", "2027-05-13") } +
            listOf(arrayOf("period", "--file", "x", "--from", "2027-01-01", "--to", "2026-01-01"))
        for (args in cases) {
            val handler = RecordingHandlers()
            val output = mutableListOf<String>()
            assertEquals(2, runApplication(args, handler, output::add), args.contentToString())
            assertEquals(listOf("Invalid arguments"), output)
            assertEquals(null, handler.called)
        }
    }

    @Test
    fun dispatchesToTheMatchingHandlerAndPreservesItsCode() {
        val cases = listOf(
            arrayOf("period", "--file", "x", "--from", "2024-02-29", "--to", "2024-02-29"),
            arrayOf("type", "--file", "x", "--type", "EXAM"),
            arrayOf("errors", "--file", "x"),
        )
        for ((code, args) in cases.withIndex()) {
            val handlers = RecordingHandlers(code)
            val output = mutableListOf<String>()
            assertEquals(code, runApplication(args, handlers, output::add))
            assertEquals(parseCommand(args), handlers.called)
            assertEquals(emptyList(), output)
        }
    }

    @Test
    fun convertsReadingFailureButDoesNotSwallowProgrammingErrors() {
        val output = mutableListOf<String>()
        val handlers = RecordingHandlers(failure = IOException("unreadable"))
        assertEquals(2, runApplication(arrayOf("errors", "--file", "x"), handlers, output::add))
        assertEquals(listOf("Cannot read file"), output)
        assertFailsWith<IllegalStateException> {
            runApplication(
                arrayOf("errors", "--file", "x"),
                RecordingHandlers(failure = IllegalStateException("bug")),
                output::add,
            )
        }
    }

    @Test
    fun readsLfCrLfBlankAndEmptyFiles(@TempDir directory: Path) {
        for ((text, expected) in listOf(
            "first\nsecond\n" to listOf(1L to "first", 2L to "second"),
            "first\r\n\r\nlast" to listOf(1L to "first", 2L to "", 3L to "last"),
            "\n" to listOf(1L to ""),
            "" to emptyList(),
        )) {
            val file = directory.resolve("input.txt")
            Files.writeString(file, text)
            val actual = mutableListOf<Pair<Long, String>>()
            forEachScheduleLine(file.toString()) { number, line -> actual.add(number to line) }
            assertEquals(expected, actual)
        }
        assertFailsWith<IOException> { forEachScheduleLine(directory.resolve("missing.txt").toString()) { _, _ -> } }
    }
}

private class RecordingHandlers(
    private val code: Int = 0,
    private val failure: Exception? = null,
) : CommandHandlers {
    var called: Command? = null

    private fun record(command: Command): Int {
        called = command
        failure?.let { throw it }
        return code
    }

    override fun period(command: Command.Period): Int = record(command)
    override fun byType(command: Command.ByType): Int = record(command)
    override fun errors(command: Command.Errors): Int = record(command)
}

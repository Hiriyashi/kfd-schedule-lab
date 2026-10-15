package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** CLI labels. The domain model is a separate student task. */
enum class SessionType { LECTURE, PRACTICE, EXAM, CANCELLED }

sealed interface Command {
    val file: String

    data class Period(
        override val file: String,
        val from: LocalDate,
        val to: LocalDate,
    ) : Command

    data class ByType(override val file: String, val type: SessionType) : Command
    data class Errors(override val file: String) : Command
}

class InvalidArgumentsException : IllegalArgumentException("Invalid arguments")

/** Only command-line arguments are parsed here, never schedule file contents. */
fun parseCommand(args: Array<String>): Command {
    val name = args.firstOrNull() ?: throw InvalidArgumentsException()
    val required = when (name) {
        "period" -> setOf("--file", "--from", "--to")
        "type" -> setOf("--file", "--type")
        "errors" -> setOf("--file")
        else -> throw InvalidArgumentsException()
    }
    if (args.size != 1 + 2 * required.size) throw InvalidArgumentsException()
    val values = mutableMapOf<String, String>()
    for (index in 1 until args.size step 2) {
        val key = args[index]
        val value = args[index + 1]
        if (key !in required || key in values || value.isBlank() || value.startsWith("--")) {
            throw InvalidArgumentsException()
        }
        values[key] = value
    }
    val file = values.getValue("--file")
    return when (name) {
        "period" -> {
            val from = parseCliDate(values.getValue("--from"))
            val to = parseCliDate(values.getValue("--to"))
            if (from > to) throw InvalidArgumentsException()
            Command.Period(file, from, to)
        }
        "type" -> {
            val type = SessionType.entries.firstOrNull { it.name == values.getValue("--type") }
                ?: throw InvalidArgumentsException()
            Command.ByType(file, type)
        }
        else -> Command.Errors(file)
    }
}

private fun parseCliDate(value: String): LocalDate {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) throw InvalidArgumentsException()
    val date = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw InvalidArgumentsException()
    }
    if (date.year !in 1..9999) throw InvalidArgumentsException()
    return date
}

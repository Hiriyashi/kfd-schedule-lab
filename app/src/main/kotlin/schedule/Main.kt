package schedule

import java.io.IOException
import java.nio.file.InvalidPathException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runApplication(args))
}

/** Ready entry point. Students implement the handlers in Commands.kt. */
fun runApplication(
    args: Array<String>,
    handlers: CommandHandlers = StudentCommandHandlers,
    output: (String) -> Unit = ::println,
): Int {
    val command = try {
        parseCommand(args)
    } catch (_: InvalidArgumentsException) {
        output("Invalid arguments")
        return 2
    }
    return try {
        when (command) {
            is Command.Period -> handlers.period(command)
            is Command.ByType -> handlers.byType(command)
            is Command.Errors -> handlers.errors(command)
        }
    } catch (_: IOException) {
        output("Cannot read file")
        2
    } catch (_: InvalidPathException) {
        output("Cannot read file")
        2
    } catch (_: SecurityException) {
        output("Cannot read file")
        2
    }
}

package build.conductor.android.client.ui.session

/** The commands that contain the name that the draft types after `/`, names that start with it first. */
fun suggestSlashCommands(draft: String, commands: List<String>): List<String> {
    val typedName = typedCommandName(draft) ?: return emptyList()
    return commands
        .filter { it.contains(typedName, ignoreCase = true) }
        .sortedWith(compareBy({ !it.startsWith(typedName, ignoreCase = true) }, { it.lowercase() }))
}

/** The draft after a chosen suggestion; the space ends the name, so the list closes. */
fun completeSlashCommand(name: String): String = "${slashCommandLabel(name)} "

fun slashCommandLabel(name: String): String = "$SLASH$name"

/** Null when the draft does not start with `/`, or when a space already ends the command name. */
private fun typedCommandName(draft: String): String? =
    draft.takeIf { it.startsWith(SLASH) }?.drop(SLASH.length)?.takeUnless { name -> name.any(Char::isWhitespace) }

private const val SLASH = "/"

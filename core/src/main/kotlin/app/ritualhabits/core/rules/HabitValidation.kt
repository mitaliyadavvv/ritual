package app.ritualhabits.core.rules

enum class HabitInputError { NAME_EMPTY, NAME_TOO_LONG, NAME_DUPLICATE, DESCRIPTION_TOO_LONG }

sealed interface HabitInput {
    data class Valid(val name: String, val description: String?) : HabitInput
    data class Invalid(val error: HabitInputError) : HabitInput
}

object HabitValidation {
    const val NAME_MAX = 60
    const val DESCRIPTION_MAX = 280

    private val whitespace = Regex("\\s+")

    fun normalizeName(raw: String): String = raw.trim().replace(whitespace, " ")

    fun normalizeDescription(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * @param existingNames names of non-deleted habits, excluding the habit being edited.
     */
    fun validate(name: String, description: String?, existingNames: Collection<String>): HabitInput {
        val n = normalizeName(name)
        val d = normalizeDescription(description)
        return when {
            n.isEmpty() -> HabitInput.Invalid(HabitInputError.NAME_EMPTY)
            n.length > NAME_MAX -> HabitInput.Invalid(HabitInputError.NAME_TOO_LONG)
            existingNames.any { normalizeName(it).equals(n, ignoreCase = true) } ->
                HabitInput.Invalid(HabitInputError.NAME_DUPLICATE)
            d != null && d.length > DESCRIPTION_MAX -> HabitInput.Invalid(HabitInputError.DESCRIPTION_TOO_LONG)
            else -> HabitInput.Valid(n, d)
        }
    }
}

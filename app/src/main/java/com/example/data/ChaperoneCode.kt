package com.example.data

/**
 * Invite codes as guardian_invite makes them (migration 020): 8 characters from an alphabet
 * without look-alikes (no 0/O, 1/I), shown as "ABCD-EF23". The server ignores case, spaces and
 * dashes; this tidies what a person types or pastes before it is sent.
 */
object ChaperoneCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** A shown code ("ABCD-EF23") inside longer text, e.g. the whole shared invitation message. */
    private val inText = Regex("(?<![A-Z0-9])([A-Z0-9]{4})-([A-Z0-9]{4})(?![A-Z0-9])")

    /** Upper case, without spaces or dashes, at most [LENGTH] characters. */
    fun normalize(input: String): String {
        val upper = input.uppercase()
        if (upper.length > LENGTH + 2) {
            // Pasted text: take the code out of it rather than its first letters.
            inText.findAll(upper).map { it.groupValues[1] + it.groupValues[2] }.lastOrNull(::isComplete)?.let { return it }
        }
        return upper.filter { it in 'A'..'Z' || it in '0'..'9' }.take(LENGTH)
    }

    /** "ABCDEF23" -> "ABCD-EF23"; a shorter start stays as it is ("ABCDE" -> "ABCD-E"). */
    fun format(code: String): String = code.chunked(4).joinToString("-")

    /** Eight characters, all from the code alphabet: worth sending to the server. */
    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** Contains a character no code has (0, O, 1 or I), so it was mistyped. */
    fun hasLookAlike(code: String): Boolean = code.any { it !in ALPHABET }
}

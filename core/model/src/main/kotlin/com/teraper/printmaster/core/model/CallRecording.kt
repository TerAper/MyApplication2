package com.teraper.printmaster.core.model

import java.time.LocalDateTime

/**
 * What a call-recording file name says. Samsung writes
 * "Запись вызовов Apo_261009_192003.m4a": a prefix in the phone's language, then the
 * contact name (or the number when it isn't a contact), then the date and time.
 */
data class RecordingName(
    /** Contact name or phone number exactly as in the file name. */
    val caller: String,
    val startedAt: LocalDateTime,
) {
    /** The caller is a number (not in the phone book), e.g. "077001020" or "+37477001020". */
    val isNumber: Boolean get() = phone != null

    /** Comparable phone digits when the caller is a number, see [ClientSearch.normalizePhone]. */
    val phone: String? get() = RecordingNames.phoneOf(caller)
}

object RecordingNames {

    private val AUDIO = setOf("m4a", "mp3", "amr", "3gp", "aac", "wav", "ogg", "opus")
    private val PATTERN = Regex("""^(.*\S)_(\d{2})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})$""")

    /** "Call recording" in the languages Samsung phones in Armenia are usually set to. */
    val KNOWN_PREFIXES = listOf("Запись вызовов", "Call recording", "Զանգի ձայնագրություն")

    /** Phone digits when [caller] is a number rather than a contact name, else null. */
    fun phoneOf(caller: String): String? =
        if (caller.count { it.isDigit() } >= 5 && caller.all { it.isDigit() || it in "+ -()" }) ClientSearch.normalizePhone(caller) else null

    /** Null when [fileName] isn't a call recording (wrong type or no date in the name). */
    fun parse(fileName: String, prefixes: List<String> = KNOWN_PREFIXES): RecordingName? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in AUDIO) return null
        val match = PATTERN.matchEntire(fileName.substringBeforeLast('.')) ?: return null
        val (head, yy, mm, dd, hh, min, ss) = match.destructured
        val startedAt = runCatching {
            LocalDateTime.of(2000 + yy.toInt(), mm.toInt(), dd.toInt(), hh.toInt(), min.toInt(), ss.toInt())
        }.getOrNull() ?: return null
        val caller = stripPrefix(head.trim(), prefixes).ifEmpty { return null }
        return RecordingName(caller, startedAt)
    }

    private fun stripPrefix(head: String, prefixes: List<String>): String {
        val prefix = prefixes.filter { head.startsWith("$it ", ignoreCase = true) }.maxByOrNull { it.length }
        return if (prefix == null) head else head.substring(prefix.length).trim()
    }

    /**
     * The prefix of a phone set to another language, learned from the files themselves:
     * the words every name starts with, as long as each still has a caller left after them.
     * Null when the names share nothing, or there are too few to tell.
     */
    fun learnPrefix(fileNames: List<String>): String? {
        val heads = fileNames.mapNotNull { name ->
            PATTERN.matchEntire(name.substringBeforeLast('.'))?.groupValues?.get(1)?.trim()?.split(' ')
        }
        if (heads.size < 3) return null
        var words = heads.first()
        for (head in heads.drop(1)) {
            words = words.zip(head).takeWhile { (a, b) -> a == b }.map { it.first }
        }
        // Keep at least one word of caller in every name.
        val shortest = heads.minOf { it.size }
        val prefix = words.take(minOf(words.size, shortest - 1))
        return prefix.joinToString(" ").ifEmpty { null }
    }
}

/** A recording found in the phone's folder and the clients it was matched to. */
data class CallRecording(
    val id: Long,
    /** Content URI of the audio file (from the folder the user picked). */
    val uri: String,
    val fileName: String,
    val caller: String,
    val startedAt: LocalDateTime,
    val durationMillis: Long,
    val clients: List<RecordingClient> = emptyList(),
    val ignored: Boolean = false,
) {
    val isMatched: Boolean get() = clients.isNotEmpty()
}

/** A client a recording belongs to. [manual] = attached by the user, not by phone number. */
data class RecordingClient(val id: Long, val name: String, val manual: Boolean = false)

/** The folder being watched and what was found in it. */
data class CallFolderStatus(
    /** Shown to the user, e.g. "Recordings/Call"; null = no folder picked yet. */
    val folderName: String?,
    val canReadContacts: Boolean,
    val total: Int = 0,
    val unmatched: Int = 0,
)

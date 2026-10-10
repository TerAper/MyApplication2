package com.teraper.printmaster.core.model

/** Where this phone stands with the company's shared space. */
data class TeamState(
    /** Firebase is set up in this build (google-services.json present). */
    val available: Boolean = false,
    /** Signed-in Google account, or null. */
    val email: String? = null,
    /** The Google account's name, to suggest as the user's name. */
    val displayName: String? = null,
    val space: TeamSpace? = null,
    /** Owner only: masters who joined. */
    val members: List<TeamMember> = emptyList(),
)

/** The shared space of one company. [joinCode] is shown to the owner only. */
data class TeamSpace(val id: String, val name: String, val isOwner: Boolean, val joinCode: String?)

/** A master who joined; [masterId] is the local master record they are linked to, if any. */
data class TeamMember(val uid: String, val name: String, val email: String, val masterId: Long?)

enum class JoinResult { JOINED, WRONG_CODE, NOT_SIGNED_IN, FAILED }

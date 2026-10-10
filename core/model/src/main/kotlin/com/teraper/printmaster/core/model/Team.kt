package com.teraper.printmaster.core.model

/** The Google account this phone is signed in with. */
data class TeamState(
    /** Firebase is set up in this build (google-services.json present). */
    val available: Boolean = false,
    /** Signed-in Google account, or null. */
    val email: String? = null,
    /** The Google account's name, to suggest as the user's name. */
    val displayName: String? = null,
)

sealed interface AttachResult {
    /** [companyName] of [ownerName] now gives the user orders. */
    data class Attached(val companyName: String, val ownerName: String) : AttachResult
    data object WrongCode : AttachResult

    /** The code of one of the user's own companies. */
    data object OwnCompany : AttachResult
    data object AlreadyAttached : AttachResult
    data object NotSignedIn : AttachResult
    data object Failed : AttachResult
}

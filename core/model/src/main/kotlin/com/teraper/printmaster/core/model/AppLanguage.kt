package com.teraper.printmaster.core.model

/** The language the app is shown in. [tag] is the BCP 47 tag; null = follow the phone. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ARMENIAN("hy"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag != null && it.tag == tag?.substringBefore('-') } ?: SYSTEM
    }
}

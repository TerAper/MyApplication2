package com.teraper.printmaster.core.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which features are used, never what's in them: event names and counts only,
 * no client names, amounts or phone numbers.
 */
interface AppAnalytics {
    fun log(event: String, params: Map<String, Any> = emptyMap())

    /** E.g. "mode" = COMPANY, so reports can be split by kind of user. */
    fun setUserProperty(name: String, value: String)
}

@Singleton
internal class FirebaseAppAnalytics @Inject constructor(@ApplicationContext context: Context) : AppAnalytics {

    private val firebase: FirebaseAnalytics? = if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAnalytics.getInstance(context) else null

    override fun log(event: String, params: Map<String, Any>) {
        val analytics = firebase ?: return
        analytics.logEvent(
            event,
            Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is Int -> putLong(key, value.toLong())
                        is Long -> putLong(key, value)
                        is Boolean -> putString(key, value.toString())
                        else -> putString(key, value.toString())
                    }
                }
            },
        )
    }

    override fun setUserProperty(name: String, value: String) {
        firebase?.setUserProperty(name, value)
    }
}

/** For tests and builds without Firebase. */
object NoAnalytics : AppAnalytics {
    override fun log(event: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
}

package com.teraper.printmaster.core.data

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.text.Spanned
import androidx.core.content.edit
import com.teraper.printmaster.core.model.CustomNames
import com.teraper.printmaster.core.model.NamedRole
import com.teraper.printmaster.core.model.RoleName
import com.teraper.printmaster.core.model.TextLanguage

/**
 * The customer's own words for Master, Order and Client, kept per text language. Screens get them
 * through [wrap]: every text the app reads from its resources comes with the words swapped in.
 */
object AppNames {
    private const val PREFS = "names"

    @Volatile private var cache: Map<TextLanguage, CustomNames> = emptyMap()

    fun read(context: Context, language: TextLanguage): CustomNames = cache[language] ?: run {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        CustomNames(
            language,
            NamedRole.entries.associateWith { role ->
                RoleName(prefs.getString(key(language, role, "one"), "").orEmpty(), prefs.getString(key(language, role, "many"), "").orEmpty())
            },
        ).also { cache = cache + (language to it) }
    }

    fun save(context: Context, names: CustomNames) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            NamedRole.entries.forEach { role ->
                val name = names.names[role] ?: RoleName()
                putString(key(names.language, role, "one"), name.one.trim())
                putString(key(names.language, role, "many"), name.many.trim())
            }
        }
        cache = cache - names.language
    }

    /** The language the app's texts are shown in for [resources]. */
    fun languageOf(resources: Resources): TextLanguage = TextLanguage.of(resources.configuration.locales[0]?.language)

    /** [base] whose texts use the customer's words; for an activity's attachBaseContext, or for notifications. */
    fun wrap(base: Context): Context = object : ContextWrapper(base) {
        private val named by lazy { NamedResources(base.resources) { read(base.applicationContext ?: base, languageOf(base.resources)) } }

        override fun getResources(): Resources = named
    }

    private fun key(language: TextLanguage, role: NamedRole, form: String) = "${language.name}.${role.name}.$form"
}

/** The app's resources with [names] swapped into every text (styled texts are left as they are). */
@Suppress("DEPRECATION")
private class NamedResources(base: Resources, private val names: () -> CustomNames) :
    Resources(base.assets, base.displayMetrics, base.configuration) {

    private fun named(text: CharSequence): CharSequence = if (text is Spanned) text else names().apply(text.toString())

    override fun getText(id: Int): CharSequence = named(super.getText(id))

    override fun getText(id: Int, def: CharSequence?): CharSequence? = super.getText(id, def)?.let(::named)

    override fun getQuantityText(id: Int, quantity: Int): CharSequence = named(super.getQuantityText(id, quantity))
}

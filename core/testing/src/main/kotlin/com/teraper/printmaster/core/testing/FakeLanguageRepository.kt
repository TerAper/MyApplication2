package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.LanguageRepository
import com.teraper.printmaster.core.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLanguageRepository(override val needsRecreate: Boolean = false) : LanguageRepository {
    val language = MutableStateFlow(AppLanguage.SYSTEM)

    override fun observeLanguage(): Flow<AppLanguage> = language

    override fun setLanguage(language: AppLanguage) {
        this.language.value = language
    }
}

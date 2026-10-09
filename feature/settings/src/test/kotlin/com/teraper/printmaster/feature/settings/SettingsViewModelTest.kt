package com.teraper.printmaster.feature.settings

import com.teraper.printmaster.core.model.AppLanguage
import com.teraper.printmaster.core.testing.FakeLanguageRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.settings.settings.SettingsEvent
import com.teraper.printmaster.feature.settings.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun pickingALanguageSavesIt() = runTest {
        val repo = FakeLanguageRepository()
        val vm = SettingsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(AppLanguage.SYSTEM, vm.uiState.value.language)
        vm.onLanguageClick(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
    }

    @Test
    fun oldAndroidRecreatesTheScreen() = runTest {
        val vm = SettingsViewModel(FakeLanguageRepository(needsRecreate = true))
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onLanguageClick(AppLanguage.ARMENIAN)
        assertEquals(SettingsEvent.Recreate, vm.events.first())
    }

    @Test
    fun languageTags() {
        assertEquals(AppLanguage.ARMENIAN, AppLanguage.fromTag("hy-AM"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("ru"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }
}

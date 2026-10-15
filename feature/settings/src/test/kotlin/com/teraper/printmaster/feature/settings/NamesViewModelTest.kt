package com.teraper.printmaster.feature.settings

import com.teraper.printmaster.core.data.repository.NamesRepository
import com.teraper.printmaster.core.model.CustomNames
import com.teraper.printmaster.core.model.NamedRole
import com.teraper.printmaster.core.model.RoleName
import com.teraper.printmaster.core.model.TextLanguage
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.settings.names.NamesEvent
import com.teraper.printmaster.feature.settings.names.NamesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NamesViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private class FakeNames : NamesRepository {
        val saved = mutableMapOf<TextLanguage, CustomNames>()
        override fun names(language: TextLanguage) = saved[language] ?: CustomNames(language)
        override fun save(names: CustomNames) {
            saved[names.language] = names
        }
    }

    @Test
    fun namesAreTypedPerLanguageAndSaved() = runTest {
        val repo = FakeNames()
        repo.save(CustomNames(TextLanguage.ARMENIAN, mapOf(NamedRole.MASTER to RoleName("Առաքիչ"))))
        val vm = NamesViewModel(repo)
        vm.onLanguage(TextLanguage.ENGLISH)
        assertTrue(vm.uiState.value.names.isEmpty())

        vm.onOneChange(NamedRole.MASTER, "Courier")
        vm.onManyChange(NamedRole.MASTER, "Couriers")
        vm.onOneChange(NamedRole.ORDER, "Delivery")
        vm.onReset(NamedRole.ORDER)
        assertTrue(vm.uiState.value.hasChanges)
        vm.onSave()

        assertEquals(NamesEvent.Saved, vm.events.first())
        assertFalse(vm.uiState.value.hasChanges)
        val english = repo.saved.getValue(TextLanguage.ENGLISH)
        assertEquals(RoleName("Courier", "Couriers"), english.names[NamedRole.MASTER])
        assertEquals("Couriers and orders", english.apply("Masters and orders"))
        // The Armenian words stay as they were.
        assertEquals("Առաքիչ", repo.saved.getValue(TextLanguage.ARMENIAN).names[NamedRole.MASTER]!!.one)
    }
}

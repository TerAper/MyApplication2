package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.data.maps.PlaceInbox
import com.teraper.printmaster.core.model.ClientDraftError
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.feature.clients.edit.ClientEditEvent
import com.teraper.printmaster.feature.clients.edit.ClientEditViewModel
import com.teraper.printmaster.feature.clients.edit.ContactList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClientEditViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeClientsRepository(listOf(summary(1, "Existing ՍՊԸ", taxId = "01234567", phone = "091 111111")))

    private val inbox = PlaceInbox()

    private fun newVm() = ClientEditViewModel(SavedStateHandle(), repo, inbox)
    private fun editVm(id: Long) = ClientEditViewModel(SavedStateHandle(mapOf("clientId" to id)), repo, inbox)

    @Test
    fun placeSharedFromTheMapFillsTheAddressRow() = runTest {
        val vm = newVm()
        vm.onContactValueChange(ContactList.ADDRESSES, 0, "Komitas")
        vm.onPickOnMap(0)
        assertEquals(ClientEditEvent.OpenMap("Komitas"), vm.events.first())

        assertTrue(inbox.deliver("Дом печати\nулица Комитаса, 5, Ереван\nhttps://yandex.ru/maps/?ll=44.503490%2C40.177200&z=17"))
        val row = vm.uiState.value.draft.addresses[0]
        assertEquals("Дом печати, улица Комитаса, 5, Ереван", row.value)
        assertEquals("geo:40.177200,44.503490?q=40.177200,44.503490", row.mapLink)

        // A share that nothing asked for is refused.
        assertFalse(inbox.deliver("Somewhere 1"))
        vm.onClearMapPoint(0)
        assertEquals(null, vm.uiState.value.draft.addresses[0].mapLink)
    }

    @Test
    fun pickedContactFillsNameAndPhone() = runTest {
        val vm = newVm()
        vm.onContactPicked(null, "Armen", "077 00 10 20")
        assertEquals("Armen", vm.uiState.value.draft.name)
        assertEquals(listOf("077 00 10 20" to ""), vm.uiState.value.draft.phones.map { it.value to it.label })

        // Name already there: the contact name becomes the phone's label, in a new row.
        vm.onContactPicked(null, "Armen accountant", "091 222222")
        assertEquals("Armen", vm.uiState.value.draft.name)
        assertEquals("091 222222" to "Armen accountant", vm.uiState.value.draft.phones[1].let { it.value to it.label })

        // Picked for a given row: replaces that number.
        vm.onContactPicked(0, "Armen", "093 333333")
        assertEquals("093 333333", vm.uiState.value.draft.phones[0].value)
        assertEquals(2, vm.uiState.value.draft.phones.size)
    }

    @Test
    fun errorsShowOnlyAfterSaveAndUpdateLive() = runTest {
        val vm = newVm()
        vm.onTaxIdChange("12")
        assertTrue(vm.uiState.value.errors.isEmpty())

        vm.onSave()
        assertEquals(setOf(ClientDraftError.NAME_REQUIRED, ClientDraftError.TAX_ID_FORMAT), vm.uiState.value.errors)

        vm.onNameChange("Firm")
        assertEquals(setOf(ClientDraftError.TAX_ID_FORMAT), vm.uiState.value.errors)
    }

    @Test
    fun taxIdKeepsOnlyDigitsAndSpaces() {
        val vm = newVm()
        vm.onTaxIdChange("01-23 45ab67")
        assertEquals("0123 4567", vm.uiState.value.draft.taxId)
    }

    @Test
    fun savingNewClientEmitsSavedAsNew() = runTest {
        val vm = newVm()
        vm.onTypeChange(ClientType.PRIVATE)
        vm.onNameChange("Աննա")
        vm.onContactValueChange(ContactList.PHONES, 0, "091 222222")
        vm.onAddContact(ContactList.PHONES)
        vm.onSave()

        assertEquals(ClientEditEvent.Saved(clientId = 2, wasNew = true), vm.events.first())
        assertEquals(listOf("091 222222"), repo.savedDrafts.single().phones.map { it.value })
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test
    fun duplicateTaxIdShowsOwnerAndClearsWhenChanged() = runTest {
        val vm = newVm()
        vm.onNameChange("Other")
        vm.onTaxIdChange("01234567")
        vm.onSave()
        assertEquals("Existing ՍՊԸ", vm.uiState.value.taxIdTakenBy)

        vm.onTaxIdChange("01234568")
        assertNull(vm.uiState.value.taxIdTakenBy)
    }

    @Test
    fun editLoadsExistingClientAndTracksChanges() = runTest {
        val vm = editVm(1)
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertEquals("Existing ՍՊԸ", state.draft.name)
        assertEquals("091 111111", state.draft.phones.single().value)
        assertFalse(state.hasChanges)

        vm.onNameChange("Renamed")
        assertTrue(vm.uiState.value.hasChanges)
        vm.onSave()
        assertEquals(ClientEditEvent.Saved(clientId = 1, wasNew = false), vm.events.first())
    }

    @Test
    fun closingWithChangesAsksFirst() = runTest {
        val vm = newVm()
        vm.onCloseRequest()
        assertEquals(ClientEditEvent.Close, vm.events.first())

        vm.onNameChange("x")
        vm.onCloseRequest()
        assertTrue(vm.uiState.value.showDiscardDialog)
        vm.onDiscardConfirmed()
        assertEquals(ClientEditEvent.Close, vm.events.first())
    }

    @Test
    fun removingLastContactLeavesOneEmptyRow() {
        val vm = newVm()
        vm.onContactValueChange(ContactList.ADDRESSES, 0, "Կոմիտաս 5")
        vm.onRemoveContact(ContactList.ADDRESSES, 0)
        assertEquals("", vm.uiState.value.draft.addresses.single().value)
    }

    @Test
    fun editingMissingClientCloses() = runTest {
        val vm = editVm(99)
        assertEquals(ClientEditEvent.Close, vm.events.first())
    }
}

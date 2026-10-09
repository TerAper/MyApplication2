package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.OfflineCallRecordingsRepository.FolderFile
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.ClientType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class OfflineCallRecordingsRepositoryTest {

    private val repos = TestRepos()
    private val calls = repos.calls

    @After
    fun tearDown() = repos.db.close()

    private suspend fun client(name: String, vararg phones: String) =
        (repos.clients.saveClient(ClientDraft(type = ClientType.PRIVATE, name = name, phones = phones.map { ContactDraft(value = it) })) as SaveClientResult.Saved).clientId

    private fun file(name: String) = FolderFile("content://test/$name", name)

    @Test
    fun recordingsAreMatchedByNumberAndName() = runTest {
        repos.register()
        val armen = client("Armen", "+374 77 001020")
        val apo = client("Apo")
        val shared = client("Second firm", "077001020")

        val result = calls.importFiles(
            listOf(
                file("Запись вызовов 077001020_261009_180042.m4a"),
                file("Запись вызовов Apo_261009_192003.m4a"),
                file("Запись вызовов 091555555_261009_200000.m4a"),
                file("notes.txt"),
            ),
        )
        assertEquals(3, result.added)
        assertEquals(2, result.matched)

        val all = calls.observeRecordings().first()
        assertEquals(listOf("091555555", "Apo", "077001020"), all.map { it.caller })
        assertEquals(LocalDateTime.of(2026, 10, 9, 18, 0, 42), all.last().startedAt)
        // One number on two clients: the call is on both cards.
        assertEquals(setOf(armen, shared), all.last().clients.map { it.id }.toSet())
        assertEquals(listOf(apo), all[1].clients.map { it.id })
        assertTrue(all.first().clients.isEmpty())
        assertEquals(1, calls.observeStatus().first().unmatched)
    }

    @Test
    fun laterClientPhoneMatchesOnNextScanAndManualAttachWorks() = runTest {
        repos.register()
        val files = listOf(file("Call recording 091555555_261009_200000.m4a"), file("Call recording Unknown_261009_210000.m4a"))
        calls.importFiles(files)
        assertEquals(2, calls.observeStatus().first().unmatched)

        val gamma = client("Gamma", "091 55 55 55")
        val result = calls.importFiles(files)
        assertEquals(0, result.added)
        assertEquals(1, result.matched)

        val unknown = calls.observeRecordings().first().first { it.caller == "Unknown" }
        calls.attach(unknown.id, gamma)
        assertEquals(2, calls.observeClientRecordings(gamma).first().size)
        assertTrue(calls.observeClientRecordings(gamma).first().first { it.caller == "Unknown" }.clients.single().manual)

        // A file deleted from the phone disappears from the app.
        calls.importFiles(files.take(1))
        assertEquals(listOf("091555555"), calls.observeRecordings().first().map { it.caller })
    }

    @Test
    fun deletingAClientRemovesOnlyTheLink() = runTest {
        repos.register()
        val id = client("Gamma", "091555555")
        calls.importFiles(listOf(file("Call recording 091555555_261009_200000.m4a")))
        repos.clients.deleteClient(id)
        val recording = calls.observeRecordings().first().single()
        assertTrue(recording.clients.isEmpty())
    }
}

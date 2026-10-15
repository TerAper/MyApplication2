package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.nearby.GeocoderNearbyRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.MapPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyRepositoryTest {

    private val repos = TestRepos()
    private val nearby = GeocoderNearbyRepository(ApplicationProvider.getApplicationContext(), repos.db.clientDao())

    @After
    fun tearDown() = repos.db.close()

    @Test
    fun aPickedPointIsUsedAsItIsAndAChangedAddressIsLookedUpAgain() = runTest {
        val draft = ClientDraft(name = "Firm", addresses = listOf(ClientDraft.ContactDraft(value = "Komitas 5", mapLink = "geo:40.2053,44.5085?q=40.2053,44.5085")))
        val id = (repos.clients.saveClient(draft) as SaveClientResult.Saved).clientId
        assertEquals(MapPoint(40.2053, 44.5085), nearby.observeAddresses().first().single().point)

        nearby.locate(null)
        val addressId = repos.db.clientDao().observeAddressPlaces().first().single().addressId
        repos.sql("UPDATE client_addresses SET map_link = NULL WHERE id = $addressId")
        // The text stayed but the point is gone: not "known" any more until looked up again.
        val after = nearby.observeAddresses().first().single()
        assertEquals(id, after.clientId)
        assertTrue(after.point == null && !after.notFound)
    }
}

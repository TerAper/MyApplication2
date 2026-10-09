package com.teraper.printmaster.core.data.maps

import com.teraper.printmaster.core.model.SharedPlace
import com.teraper.printmaster.core.model.SharedPlaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a place from a map app back to the form that asked for it. Map apps can't return a
 * picked point, so the form opens the map, the user shares the place to this app, and the
 * share lands here.
 */
@Singleton
class PlaceInbox @Inject constructor() {

    private val _place = MutableStateFlow<SharedPlace?>(null)

    /** A place waiting to be taken by the form. */
    val place: StateFlow<SharedPlace?> = _place.asStateFlow()

    /** True between [startPicking] and the share arriving: a form is waiting. */
    var isWaiting: Boolean = false
        private set

    fun startPicking() {
        isWaiting = true
        _place.value = null
    }

    /** Text shared from a map app. False when nothing asked for a place, or the text has none. */
    fun deliver(text: String): Boolean {
        if (!isWaiting) return false
        val place = SharedPlaces.parse(text) ?: return false
        isWaiting = false
        _place.value = place
        return true
    }

    fun take(): SharedPlace? = _place.value.also { _place.value = null }
}

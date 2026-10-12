package com.teraper.printmaster.feature.orders.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.DeleteOrderResult
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import com.teraper.printmaster.feature.orders.navigation.ORDER_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrderDetailDialog { CONFIRM_DELETE, DELETE_BLOCKED, CONFIRM_CANCEL, CONFIRM_REOPEN, DECLINE }

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState
    data object NotFound : OrderDetailUiState
    data class Loaded(
        val order: Order,
        val today: LocalDate,
        val work: OrderWork = OrderWork(),
        val dialog: OrderDetailDialog? = null,
        /** Recorded calls with the client on the order's day. */
        val calls: List<CallRecording> = emptyList(),
        val playing: CallRecording? = null,
        val photos: List<Photo> = emptyList(),
        /** Accepting an order given as his own: choosing which of his companies it goes into. */
        val choosingCompany: Boolean = false,
        /** The user's own companies, for an order given to him as his own. */
        val ownCompanies: List<Company> = emptyList(),
        val declineReason: String = "",
    ) : OrderDetailUiState {
        /**
         * Work can be added or changed only while the order is open, and not on another owner's order
         * before it's accepted, nor (owner) on an order given to an attached master as his.
         */
        val canEditWork: Boolean get() = order.isOpen && !work.isBilled && !order.awaitsAnswer && !order.declinedHere && !givenAway

        /** Owner's phone: given to an attached master as his own: his work, his money. */
        val givenAway: Boolean get() = order.handedOver && order.masterAttached && !order.fromAttachedCompany && order.takenFrom == null

        /** With work done, the order is finished by charging the client instead of "Mark done". */
        val canFinishWithWork: Boolean get() = canEditWork && work.total.isPositive
    }
}

sealed interface OrderDetailEvent {
    data object Deleted : OrderDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ordersRepository: OrdersRepository,
    private val repairsRepository: RepairsRepository,
    companiesRepository: CompaniesRepository,
    callRecordingsRepository: CallRecordingsRepository,
    private val photoRepository: PhotoRepository,
    clock: Clock,
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle[ORDER_ID_ARG])
    private val today = LocalDate.now(clock)
    private val dialog = MutableStateFlow<OrderDetailDialog?>(null)
    private val deleting = MutableStateFlow(false)
    private val playing = MutableStateFlow<CallRecording?>(null)
    private val choosingCompany = MutableStateFlow(false)
    private val declineReason = MutableStateFlow("")

    private val _events = Channel<OrderDetailEvent>(Channel.BUFFERED)
    val events: Flow<OrderDetailEvent> = _events.receiveAsFlow()

    private val order = ordersRepository.observeOrder(orderId)

    /**
     * The client's calls about this order: on the day it was written down (when the client
     * usually calls) and on the day of the visit.
     */
    private val calls = order.map { it?.clientId to setOfNotNull(it?.date, it?.createdAt?.toLocalDate()) }.distinctUntilChanged().flatMapLatest { (clientId, days) ->
        if (clientId == null) {
            flowOf(emptyList())
        } else {
            callRecordingsRepository.observeClientRecordings(clientId).map { list -> list.filter { it.startedAt.toLocalDate() in days } }
        }
    }

    init {
        // A call that just ended shows up here without waiting for the background check.
        viewModelScope.launch { callRecordingsRepository.refresh() }
    }

    private val view = combine(
        combine(dialog, deleting, playing, ::Triple),
        photoRepository.observePhotos(PhotoOwner.ORDER, listOf(orderId)),
        choosingCompany,
        companiesRepository.observeCompanies(),
        declineReason,
    ) { (dialog, deleting, playing), photos, choosing, companies, reason ->
        ViewState(dialog, deleting, playing, photos, choosing, companies, reason)
    }

    private data class ViewState(
        val dialog: OrderDetailDialog?,
        val deleting: Boolean,
        val playing: CallRecording?,
        val photos: List<Photo>,
        val choosingCompany: Boolean,
        val companies: List<Company>,
        val declineReason: String,
    )

    val uiState: StateFlow<OrderDetailUiState> = combine(
        order,
        repairsRepository.observeOrderWork(orderId),
        calls,
        view,
    ) { order, work, calls, view ->
        when {
            order != null -> OrderDetailUiState.Loaded(
                order, today, work, view.dialog, calls, view.playing, view.photos, view.choosingCompany, view.companies, view.declineReason,
            )
            view.deleting -> OrderDetailUiState.Loading
            else -> OrderDetailUiState.NotFound
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState.Loading)

    /** Cancelling asks first, and so does reopening a billed order; other changes are one tap. */
    fun onStatusChange(status: OrderStatus) {
        val billed = (uiState.value as? OrderDetailUiState.Loaded)?.work?.isBilled == true
        when {
            status == OrderStatus.CANCELLED -> dialog.value = OrderDetailDialog.CONFIRM_CANCEL
            status == OrderStatus.NEW && billed -> dialog.value = OrderDetailDialog.CONFIRM_REOPEN
            else -> viewModelScope.launch { ordersRepository.setStatus(orderId, status) }
        }
    }

    /**
     * Charges the client for the work and closes the order; [paidInCash] also records the cash.
     * Done for another company, its client is charged there, not here.
     */
    fun onFinish(paidInCash: Boolean) {
        viewModelScope.launch { repairsRepository.finishOrder(orderId, paidInCash) }
    }

    /** Yes to an order from another company. Given as his own: into his only company, or he picks one. */
    fun onAccept() {
        val state = uiState.value as? OrderDetailUiState.Loaded ?: return
        val companies = state.ownCompanies
        when {
            !state.order.handedOver -> viewModelScope.launch { repairsRepository.acceptOrder(orderId, null) }
            companies.size == 1 -> viewModelScope.launch { repairsRepository.acceptOrder(orderId, companies.single().id) }
            else -> choosingCompany.value = true
        }
    }

    fun onAcceptInto(companyId: Long) {
        choosingCompany.value = false
        viewModelScope.launch { repairsRepository.acceptOrder(orderId, companyId) }
    }

    fun onDismissCompanyChoice() = choosingCompany.update { false }

    /** An attached company's order: turned down with a reason, it goes back to that company. */
    fun onDeclineClick() {
        declineReason.value = ""
        dialog.value = OrderDetailDialog.DECLINE
    }

    fun onDeclineReasonChange(reason: String) = declineReason.update { reason }

    fun onConfirmDecline() {
        dialog.value = null
        viewModelScope.launch { repairsRepository.declineOrder(orderId, declineReason.value) }
    }

    /** Removes the charge (and cash) made when the order was finished, so the work can change. */
    fun onConfirmReopen() {
        dialog.value = null
        viewModelScope.launch { repairsRepository.reopenOrder(orderId) }
    }

    fun onConfirmCancel() {
        dialog.value = null
        viewModelScope.launch { ordersRepository.setStatus(orderId, OrderStatus.CANCELLED) }
    }

    fun onDeleteClick() = dialog.update { OrderDetailDialog.CONFIRM_DELETE }

    fun onPlayCall(recording: CallRecording) = playing.update { recording }

    fun onStopCall() = playing.update { null }

    fun onAddPhoto(uri: String) {
        viewModelScope.launch { photoRepository.add(PhotoOwner.ORDER, orderId, uri) }
    }

    fun onDeletePhoto(photo: Photo) {
        viewModelScope.launch { photoRepository.delete(photo.id) }
    }

    fun onConfirmDelete() {
        dialog.value = null
        viewModelScope.launch {
            deleting.value = true
            when (ordersRepository.deleteOrder(orderId)) {
                DeleteOrderResult.DELETED, DeleteOrderResult.NOT_FOUND -> _events.send(OrderDetailEvent.Deleted)
                DeleteOrderResult.HAS_RECORDS -> {
                    deleting.value = false
                    dialog.value = OrderDetailDialog.DELETE_BLOCKED
                }
            }
        }
    }

    fun onDismissDialog() = dialog.update { null }
}

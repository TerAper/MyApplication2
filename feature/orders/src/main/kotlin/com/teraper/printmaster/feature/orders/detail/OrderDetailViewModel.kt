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
        /** An attached company's order being finished (paid in cash or not): asking whose order it is. */
        val finishChoice: Boolean? = null,
        /** The user's own companies, to count an attached company's order as one of theirs. */
        val ownCompanies: List<Company> = emptyList(),
        val declineReason: String = "",
    ) : OrderDetailUiState {
        /** Work can be added or changed only while the order is open. */
        val canEditWork: Boolean get() = order.isOpen && !work.isBilled

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
    private val finishChoice = MutableStateFlow<Boolean?>(null)
    private val declineReason = MutableStateFlow("")

    private val _events = Channel<OrderDetailEvent>(Channel.BUFFERED)
    val events: Flow<OrderDetailEvent> = _events.receiveAsFlow()

    private val order = ordersRepository.observeOrder(orderId)

    private val calls = order.map { it?.clientId to it?.date }.distinctUntilChanged().flatMapLatest { (clientId, date) ->
        if (clientId == null) {
            flowOf(emptyList())
        } else {
            callRecordingsRepository.observeClientRecordings(clientId).map { list -> list.filter { it.startedAt.toLocalDate() == date } }
        }
    }

    private val view = combine(
        combine(dialog, deleting, playing, ::Triple),
        photoRepository.observePhotos(PhotoOwner.ORDER, listOf(orderId)),
        finishChoice,
        companiesRepository.observeCompanies(),
        declineReason,
    ) { (dialog, deleting, playing), photos, finishChoice, companies, reason ->
        ViewState(dialog, deleting, playing, photos, finishChoice, companies, reason)
    }

    private data class ViewState(
        val dialog: OrderDetailDialog?,
        val deleting: Boolean,
        val playing: CallRecording?,
        val photos: List<Photo>,
        val finishChoice: Boolean?,
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
                order, today, work, view.dialog, calls, view.playing, view.photos, view.finishChoice, view.companies, view.declineReason,
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
     * An attached company's order first asks whose it is.
     */
    fun onFinish(paidInCash: Boolean) {
        if ((uiState.value as? OrderDetailUiState.Loaded)?.order?.fromAttachedCompany == true) {
            finishChoice.value = paidInCash
            return
        }
        viewModelScope.launch { repairsRepository.finishOrder(orderId, paidInCash) }
    }

    /** [companyId] null = done for the attached company (its client owes it); else counted in that own company. */
    fun onFinishFor(companyId: Long?) {
        val paidInCash = finishChoice.value ?: return
        finishChoice.value = null
        viewModelScope.launch {
            if (companyId == null) repairsRepository.finishOrder(orderId, paidInCash) else repairsRepository.finishAsMine(orderId, companyId, paidInCash)
        }
    }

    fun onDismissFinishChoice() = finishChoice.update { null }

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

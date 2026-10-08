package com.teraper.printmaster.feature.orders.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.DeleteOrderResult
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.feature.orders.navigation.ORDER_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class OrderDetailDialog { CONFIRM_DELETE, DELETE_BLOCKED, CONFIRM_CANCEL, CONFIRM_REOPEN }

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState
    data object NotFound : OrderDetailUiState
    data class Loaded(
        val order: Order,
        val today: LocalDate,
        val work: OrderWork = OrderWork(),
        val dialog: OrderDetailDialog? = null,
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

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ordersRepository: OrdersRepository,
    private val repairsRepository: RepairsRepository,
    clock: Clock,
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle[ORDER_ID_ARG])
    private val today = LocalDate.now(clock)
    private val dialog = MutableStateFlow<OrderDetailDialog?>(null)
    private val deleting = MutableStateFlow(false)

    private val _events = Channel<OrderDetailEvent>(Channel.BUFFERED)
    val events: Flow<OrderDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<OrderDetailUiState> = combine(
        ordersRepository.observeOrder(orderId),
        repairsRepository.observeOrderWork(orderId),
        dialog,
        deleting,
    ) { order, work, dialog, deleting ->
        when {
            order != null -> OrderDetailUiState.Loaded(order, today, work, dialog)
            deleting -> OrderDetailUiState.Loading
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

    /** Charges the client for the work and closes the order; [paidInCash] also records the cash. */
    fun onFinish(paidInCash: Boolean) {
        viewModelScope.launch { repairsRepository.finishOrder(orderId, paidInCash) }
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

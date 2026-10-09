package com.teraper.printmaster.core.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Runs [block] for the active company and starts over when the user switches; [empty] before registration. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> CompaniesRepository.forActiveCompany(empty: T, block: (companyId: Long) -> Flow<T>): Flow<T> =
    observeActiveCompany().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(empty) else block(id)
    }

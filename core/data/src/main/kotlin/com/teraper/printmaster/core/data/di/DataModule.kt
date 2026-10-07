package com.teraper.printmaster.core.data.di

import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.OfflinePrintersRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.PrintersRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindClientsRepository(impl: OfflineClientsRepository): ClientsRepository

    @Binds
    abstract fun bindPaymentsRepository(impl: OfflinePaymentsRepository): PaymentsRepository

    @Binds
    abstract fun bindCatalogRepository(impl: OfflineCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindPrintersRepository(impl: OfflinePrintersRepository): PrintersRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}

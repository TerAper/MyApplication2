package com.teraper.printmaster.core.data.di

import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflineOrdersRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.OfflinePriceListRepository
import com.teraper.printmaster.core.data.repository.OfflinePrintersRepository
import com.teraper.printmaster.core.data.repository.OfflineRepairsRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.data.repository.PrintersRepository
import com.teraper.printmaster.core.data.repository.RepairsRepository
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
    abstract fun bindCompaniesRepository(impl: OfflineCompaniesRepository): CompaniesRepository

    @Binds
    abstract fun bindOrdersRepository(impl: OfflineOrdersRepository): OrdersRepository

    @Binds
    abstract fun bindCatalogRepository(impl: OfflineCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindPrintersRepository(impl: OfflinePrintersRepository): PrintersRepository

    @Binds
    abstract fun bindPriceListRepository(impl: OfflinePriceListRepository): PriceListRepository

    @Binds
    abstract fun bindRepairsRepository(impl: OfflineRepairsRepository): RepairsRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}

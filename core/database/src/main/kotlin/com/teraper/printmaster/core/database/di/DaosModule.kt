package com.teraper.printmaster.core.database.di

import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CallRecordingDao
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.ImportDao
import com.teraper.printmaster.core.database.dao.LedgerDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.PriceListDao
import com.teraper.printmaster.core.database.dao.RepairDao
import com.teraper.printmaster.core.database.dao.ReportDao
import com.teraper.printmaster.core.database.dao.SyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule {
    @Provides
    fun provideClientDao(database: PrintMasterDatabase): ClientDao = database.clientDao()

    @Provides
    fun provideLedgerDao(database: PrintMasterDatabase): LedgerDao = database.ledgerDao()

    @Provides
    fun provideCatalogDao(database: PrintMasterDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun provideCompanyDao(database: PrintMasterDatabase): CompanyDao = database.companyDao()

    @Provides
    fun provideOrderDao(database: PrintMasterDatabase): OrderDao = database.orderDao()

    @Provides
    fun providePriceListDao(database: PrintMasterDatabase): PriceListDao = database.priceListDao()

    @Provides
    fun provideRepairDao(database: PrintMasterDatabase): RepairDao = database.repairDao()

    @Provides
    fun provideReportDao(database: PrintMasterDatabase): ReportDao = database.reportDao()

    @Provides
    fun provideCallRecordingDao(database: PrintMasterDatabase): CallRecordingDao = database.callRecordingDao()

    @Provides
    fun provideImportDao(database: PrintMasterDatabase): ImportDao = database.importDao()

    @Provides
    fun provideSyncDao(database: PrintMasterDatabase): SyncDao = database.syncDao()
}

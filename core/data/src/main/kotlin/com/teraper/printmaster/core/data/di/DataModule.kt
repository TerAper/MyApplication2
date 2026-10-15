package com.teraper.printmaster.core.data.di

import com.teraper.printmaster.core.data.repository.AndroidLanguageRepository
import com.teraper.printmaster.core.data.repository.BackupRepository
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.ExportRepository
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflineExportRepository
import com.teraper.printmaster.core.data.repository.OfflineImportRepository
import com.teraper.printmaster.core.data.repository.OfflineReportsRepository
import com.teraper.printmaster.core.data.repository.OfflineOrdersRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.LanguageRepository
import com.teraper.printmaster.core.data.repository.ImportColumnsRepository
import com.teraper.printmaster.core.data.repository.PrefsImportColumnsRepository
import com.teraper.printmaster.core.data.repository.OfflineBackupRepository
import com.teraper.printmaster.core.data.repository.OfflineCallRecordingsRepository
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.OfflinePriceListRepository
import com.teraper.printmaster.core.data.repository.OfflineClientItemsRepository
import com.teraper.printmaster.core.data.repository.OfflineRepairsRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.data.repository.ClientListSettingsRepository
import com.teraper.printmaster.core.data.repository.OfflineWarehouseRepository
import com.teraper.printmaster.core.data.repository.WarehouseRepository
import com.teraper.printmaster.core.data.repository.PrefsClientListSettingsRepository
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.data.repository.ReportsRepository
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.data.analytics.FirebaseAppAnalytics
import com.teraper.printmaster.core.data.sync.FirebaseSyncBackend
import com.teraper.printmaster.core.data.sync.SyncBackend
import com.teraper.printmaster.core.data.team.FirebaseTeamRepository
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.data.repository.OfflinePhotoRepository
import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.data.repository.ExpensesRepository
import com.teraper.printmaster.core.data.repository.OfflineExpensesRepository
import com.teraper.printmaster.core.data.repository.NamesRepository
import com.teraper.printmaster.core.data.repository.PrefsNamesRepository
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
    abstract fun bindClientItemsRepository(impl: OfflineClientItemsRepository): ClientItemsRepository

    @Binds
    abstract fun bindWarehouseRepository(impl: OfflineWarehouseRepository): WarehouseRepository

    @Binds
    abstract fun bindClientListSettingsRepository(impl: PrefsClientListSettingsRepository): ClientListSettingsRepository

    @Binds
    abstract fun bindPriceListRepository(impl: OfflinePriceListRepository): PriceListRepository

    @Binds
    abstract fun bindRepairsRepository(impl: OfflineRepairsRepository): RepairsRepository

    @Binds
    abstract fun bindBackupRepository(impl: OfflineBackupRepository): BackupRepository

    @Binds
    abstract fun bindReportsRepository(impl: OfflineReportsRepository): ReportsRepository

    @Binds
    abstract fun bindExportRepository(impl: OfflineExportRepository): ExportRepository

    @Binds
    abstract fun bindLanguageRepository(impl: AndroidLanguageRepository): LanguageRepository

    @Binds
    abstract fun bindImportColumnsRepository(impl: PrefsImportColumnsRepository): ImportColumnsRepository

    @Binds
    abstract fun bindCallRecordingsRepository(impl: OfflineCallRecordingsRepository): CallRecordingsRepository

    @Binds
    abstract fun bindImportRepository(impl: OfflineImportRepository): ImportRepository

    @Binds
    abstract fun bindTeamRepository(impl: FirebaseTeamRepository): TeamRepository

    @Binds
    abstract fun bindSyncBackend(impl: FirebaseSyncBackend): SyncBackend

    @Binds
    abstract fun bindAnalytics(impl: FirebaseAppAnalytics): AppAnalytics

    @Binds
    abstract fun bindSyncController(impl: com.teraper.printmaster.core.data.sync.SyncRunner): com.teraper.printmaster.core.data.sync.SyncController

    @Binds
    abstract fun bindPhotoRepository(impl: OfflinePhotoRepository): PhotoRepository

    @Binds
    abstract fun bindExpensesRepository(impl: OfflineExpensesRepository): ExpensesRepository

    @Binds
    abstract fun bindNamesRepository(impl: PrefsNamesRepository): NamesRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}

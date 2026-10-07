package com.teraper.printmaster.core.database.di

import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.LedgerDao
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
}

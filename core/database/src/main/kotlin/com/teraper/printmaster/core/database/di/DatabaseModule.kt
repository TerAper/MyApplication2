package com.teraper.printmaster.core.database.di

import android.content.Context
import com.teraper.printmaster.core.database.PrintMasterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PrintMasterDatabase =
        PrintMasterDatabase.create(context)
}

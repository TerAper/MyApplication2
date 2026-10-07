package com.teraper.printmaster.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private const val DATABASE_NAME = "printmaster.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PrintMasterDatabase =
        Room.databaseBuilder(context, PrintMasterDatabase::class.java, DATABASE_NAME)
            .addCallback(
                object : RoomDatabase.Callback() {
                    // SQLite ignores foreign keys unless asked; we rely on them for
                    // cascades (delete client → phones) and restrictions (client with orders).
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                },
            )
            .build()
}

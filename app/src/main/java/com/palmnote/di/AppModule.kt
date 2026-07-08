package com.palmnote.di

import android.content.Context
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.lock.AppLockManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun provideCsvDataExporter(
        @ApplicationContext context: Context,
        db: AppDatabase,
        preferencesManager: PreferencesManager
    ): CsvDataExporter = CsvDataExporter(context, db, preferencesManager)

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        db: AppDatabase
    ): BackupRepository = BackupRepository(context, db)

    @Provides
    @Singleton
    fun provideAppLockManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): AppLockManager = AppLockManager(context, preferencesManager)
}

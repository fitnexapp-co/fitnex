package com.fitnex.core.database.di

import android.content.Context
import androidx.room.Room
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.database.FitnexMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): FitnexDatabase =
        Room.databaseBuilder(context, FitnexDatabase::class.java, FitnexDatabase.NAME)
            .addMigrations(*FitnexMigrations.ALL)
            .addCallback(FitnexDatabase.syncCallback)
            .build()

    @Provides fun userDao(db: FitnexDatabase) = db.userDao()
    @Provides fun catalogDao(db: FitnexDatabase) = db.catalogDao()
    @Provides fun workoutDao(db: FitnexDatabase) = db.workoutDao()
    @Provides fun sessionDao(db: FitnexDatabase) = db.sessionDao()
    @Provides fun calendarDao(db: FitnexDatabase) = db.calendarDao()
    @Provides fun nutritionDao(db: FitnexDatabase) = db.nutritionDao()
    @Provides fun bodyDao(db: FitnexDatabase) = db.bodyDao()
    @Provides fun aiDao(db: FitnexDatabase) = db.aiDao()
    @Provides fun accountDao(db: FitnexDatabase) = db.accountDao()
    @Provides fun syncDao(db: FitnexDatabase) = db.syncDao()
}

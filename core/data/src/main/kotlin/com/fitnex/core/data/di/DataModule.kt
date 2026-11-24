package com.fitnex.core.data.di

import com.fitnex.core.data.catalog.AssetCatalogSource
import com.fitnex.core.data.catalog.CatalogSource
import com.fitnex.core.data.profile.RoomUserProfileRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoomExerciseRepository
import com.fitnex.core.data.training.RoomRoutineRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.data.profile.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindsUserProfileRepository(impl: RoomUserProfileRepository): UserProfileRepository

    @Binds
    fun bindsActiveProfileSource(impl: com.fitnex.core.data.profile.StoredActiveProfile): com.fitnex.core.data.session.ActiveProfileSource

    @Binds
    fun bindsExerciseRepository(impl: RoomExerciseRepository): ExerciseRepository

    @Binds
    fun bindsRoutineRepository(impl: RoomRoutineRepository): RoutineRepository

    @Binds
    fun bindsCatalogSource(impl: AssetCatalogSource): CatalogSource

    @Binds
    fun bindsWorkoutCalendarRepository(impl: com.fitnex.core.data.calendar.RoomWorkoutCalendarRepository): com.fitnex.core.data.calendar.WorkoutCalendarRepository

    @Binds
    fun bindsGlobalStatisticsRepository(impl: com.fitnex.core.data.stats.RoomGlobalStatisticsRepository): com.fitnex.core.data.stats.GlobalStatisticsRepository

    @Binds
    fun bindsFoodCatalogSource(impl: com.fitnex.core.data.nutrition.AssetFoodCatalogSource): com.fitnex.core.data.nutrition.FoodCatalogSource

    @Binds
    fun bindsFoodCatalogRepository(impl: com.fitnex.core.data.nutrition.AssetFoodCatalogRepository): com.fitnex.core.data.nutrition.FoodCatalogRepository

    @Binds
    fun bindsNutritionRepository(impl: com.fitnex.core.data.nutrition.RoomNutritionRepository): com.fitnex.core.data.nutrition.NutritionRepository

    @Binds
    fun bindsAiRepository(impl: com.fitnex.core.data.ai.BackendAiRepository): com.fitnex.core.data.ai.AiRepository

    @Binds
    fun bindsWorkoutSessionRepository(impl: com.fitnex.core.data.training.RoomWorkoutSessionRepository): com.fitnex.core.data.training.WorkoutSessionRepository
}

/** Separate so UI tests can swap the cloud for a fake (no network in tests). */
@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds
    fun bindsCloudSync(impl: com.fitnex.core.data.sync.BackendCloudSync): com.fitnex.core.data.sync.CloudSync
}

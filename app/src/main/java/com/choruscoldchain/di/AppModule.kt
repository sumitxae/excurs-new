package com.choruscoldchain.di

import android.content.Context
import com.choruscoldchain.permissions.PermissionCoordinator
import com.choruscoldchain.permissions.PermissionManager
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
    fun providePermissionManager(@ApplicationContext context: Context): PermissionManager {
        return PermissionManager(context)
    }
    
    @Provides
    @Singleton
    fun providePermissionCoordinator(
        @ApplicationContext context: Context,
        permissionManager: PermissionManager
    ): PermissionCoordinator {
        return PermissionCoordinator(context, permissionManager)
    }
}

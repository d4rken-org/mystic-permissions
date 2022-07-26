package eu.darken.myperm.common.dagger

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AndroidModule {

    @Provides
    @Singleton
    fun context(app: Application): Context = app.applicationContext

    @Provides
    @Singleton
    fun packagemanager(@ApplicationContext context: Context): PackageManager = context.packageManager

    @Provides
    @Singleton
    fun notificationManager(context: Context): NotificationManagerCompat =
        NotificationManagerCompat.from(context)
}

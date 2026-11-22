package com.pulldock.core.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Battery level observation for the threshold policy (T106, US7 scenario 3). The
 * implementation reads the sticky BATTERY_STATUS intent — no receiver leaks, no
 * continuous registration; each read is a fresh sticky query.
 */
interface BatteryMonitor {
    /** Current battery percent, or null when the platform cannot answer. */
    fun currentPercent(): Int?
}

class BatteryMonitorImpl @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : BatteryMonitor {

    override fun currentPercent(): Int? = runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) null else (level * 100) / scale
    }.getOrNull()
}

@Module
@InstallIn(SingletonComponent::class)
object BatteryModule {

    @Provides
    @Singleton
    fun provideBatteryMonitor(impl: BatteryMonitorImpl): BatteryMonitor = impl
}

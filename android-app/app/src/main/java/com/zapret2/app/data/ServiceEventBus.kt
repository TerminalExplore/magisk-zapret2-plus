package com.zapret2.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceEventBus @Inject constructor(
    @ApplicationContext val appContext: Context
) {
    private val _serviceRestarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val serviceRestarted = _serviceRestarted.asSharedFlow()

    fun notifyServiceRestarted() {
        _serviceRestarted.tryEmit(Unit)
    }
}

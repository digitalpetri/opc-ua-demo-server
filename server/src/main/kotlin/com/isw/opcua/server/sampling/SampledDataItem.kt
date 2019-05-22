package com.isw.opcua.server.sampling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.api.DataItem
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue

abstract class SampledDataItem(
    protected val item: DataItem,
    protected val scope: CoroutineScope,
    private val tickManager: TickManager
) : AbstractLifecycle() {

    @Volatile
    var samplingEnabled: Boolean = true

    private var tick: TickManager.Tick? = null

    private suspend fun tick(currentTime: Long) {
        if (samplingEnabled) {
            try {
                item.setValue(sampleCurrentValue(currentTime))
            } catch (t: Throwable) {
                item.setValue(DataValue(StatusCodes.Bad_InternalError))
            }
        }
    }

    override fun onStartup() {
        scope.launch {
            item.setValue(sampleInitialValue(System.currentTimeMillis()))

            synchronized(this) {
                if (super.isRunning()) {
                    tick = tickManager.registerForTick(item.samplingInterval.toLong()) { tick(it) }
                }
            }
        }
    }

    override fun onShutdown(): Unit = synchronized(this) {
        tick?.cancel()
    }

    fun modifyRate(newRate: Double) {
        tick?.modify(newRate.toLong())
    }

    protected abstract suspend fun sampleCurrentValue(currentTime: Long): DataValue

    protected open suspend fun sampleInitialValue(currentTime: Long): DataValue = sampleCurrentValue(currentTime)

}

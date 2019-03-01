package com.isw.opcua.server.util

import java.util.concurrent.atomic.AtomicReference

abstract class AbstractLifecycle {

    private enum class LifecycleState {
        NEW, RUNNING, STOPPED
    }

    private val state = AtomicReference(LifecycleState.NEW)

    /**
     * @return true after [startup] is called and before [shutdown] is called.
     */
    val running: Boolean
        get() = state.get() == LifecycleState.RUNNING

    /**
     * Call to start this thing up. The first time this is called, [onStartup] will be called.
     *
     * Subsequent invocations will throw an [IllegalStateException].
     */
    fun startup() {
        val previous = state.getAndUpdate { prev ->
            when (prev) {
                LifecycleState.NEW -> LifecycleState.RUNNING
                else -> prev
            }
        }

        if (previous == LifecycleState.NEW) {
            this.onStartup()
        } else {
            throw IllegalStateException("cannot call startup when state=$previous")
        }
    }

    /**
     * Call to shut this thing down. Must have called startup first for this to have an effect.
     */
    fun shutdown() {
        val previous = state.getAndUpdate { prev ->
            when (prev) {
                LifecycleState.RUNNING -> LifecycleState.STOPPED
                else -> prev
            }
        }

        if (previous == LifecycleState.RUNNING) {
            this.onShutdown()
        } else if (previous == LifecycleState.NEW) {
            throw IllegalStateException("cannot call shutdown, never started")
        }
    }

    protected abstract fun onStartup()

    protected abstract fun onShutdown()


}

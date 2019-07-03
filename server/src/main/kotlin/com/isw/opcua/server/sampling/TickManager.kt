package com.isw.opcua.server.sampling

import com.google.common.collect.Maps
import com.google.common.collect.Sets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentMap


private typealias Callback = suspend (Long) -> Unit

class TickManager(private val coroutineScope: CoroutineScope) {

    private val logger: Logger = LoggerFactory.getLogger(TickManager::class.java)

    private val tickerJobs: ConcurrentMap<Long, Job> = Maps.newConcurrentMap()

    private val callbackMap: ConcurrentMap<Long, MutableSet<Callback>> = Maps.newConcurrentMap()


    /**
     * Register a [SendChannel] to receive a message every [rateMillis] milliseconds.
     */
    fun registerForTick(
        rateMillis: Long,
        callback: suspend (Long) -> Unit
    ): Tick = synchronized(this) {

        require(rateMillis > 0L) {
            "rate: $rateMillis <= 0"
        }

        callbackMap
            .getOrPut(rateMillis) { Sets.newConcurrentHashSet() }
            .add(callback)

        checkTickerJobs()

        return object : Tick {

            var currentRate = rateMillis

            override fun cancel() = synchronized(this@TickManager) {
                callbackMap[currentRate]?.remove(callback)

                checkTickerJobs()
            }

            override fun modify(newRateMillis: Long) = synchronized(this@TickManager) {
                require(rateMillis > 0L) {
                    "rate: $rateMillis <= 0"
                }


                callbackMap[currentRate]?.remove(callback)

                currentRate = newRateMillis

                callbackMap
                    .getOrPut(newRateMillis) { Sets.newConcurrentHashSet() }
                    .add(callback)

                checkTickerJobs()
            }

        }
    }

    private fun checkTickerJobs() = synchronized(this) {
        callbackMap.entries.forEach { (rate, callbackList) ->
            if (callbackList.isEmpty()) {
                logger.debug("cancelling ticker job @ ${rate}ms")
                callbackMap.remove(rate)
                tickerJobs.remove(rate)?.cancel()
            } else if (!tickerJobs.containsKey(rate)) {
                logger.debug("launching ticker job @ ${rate}ms")

                tickerJobs[rate] = coroutineScope.launch {
                    while (true) {
                        val startTime = System.currentTimeMillis()
                        val callbacksAtRate = callbackMap[rate]
                        callbacksAtRate?.forEach { it.invoke(startTime) }
                        val elapsedTime = System.currentTimeMillis() - startTime

                        if (elapsedTime > rate) {
                            logger.warn("aggregate callback time exceeded tick rate: ${elapsedTime}ms > ${rate}ms")
                            logger.warn("${callbacksAtRate?.size ?: 0} callbacks registered at ${rate}ms")
                        }

                        delay(rate)
                    }
                }
            }
        }
    }

    interface Tick {

        /**
         * Cancel this Tick. The registered channel will stop receiving values.
         */
        fun cancel()

        /**
         * Modify the rate of this Tick.
         */
        fun modify(newRateMillis: Long)

    }

}


package com.coroutines.project

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

suspend fun <T> retry(
    retryLimit: Int = 3,
    initialDelayMs: Long = 0,
    backoffStrategy: BackoffStrategy = BackoffStrategyFixed(),
    useJitter: Boolean = false,
    errorType: KClass<*> = Throwable::class,
    maximalWaitTimeMs: Long = 0,
    block: suspend () -> T
): T {
    var lastRecordedException: Throwable? = null

    return withTimeout(maximalWaitTimeMs.takeIf { it > 0 } ?: Long.MAX_VALUE) {
        delay(initialDelayMs)
        repeat(retryLimit) {
            runCatching {
                block()
            }
                .onSuccess { return@withTimeout it }
                .onFailure { exception ->
                    lastRecordedException = exception
                    if (!exception::class.isSubclassOf(errorType)) {
                        throw exception
                    }
                }

            delay(backoffStrategy.getInterval())

            if (useJitter) {
                delay(Random.nextLong(1000))
            }
        }

        throw lastRecordedException ?: error("Missing last recorded exception")
    }
}

interface BackoffStrategy {
    fun getInterval(): Long
}

class BackoffStrategyFixed(private val fixedIntervalMs: Long = 1000) : BackoffStrategy {

    init {
        check(fixedIntervalMs > 0) { "Fixed interval must be greater than 0" }
    }

    override fun getInterval(): Long {
        return fixedIntervalMs
    }
}

class BackoffStrategyExponential(private val startInterval: Long) : BackoffStrategy {

    init {
        check(startInterval > 0) { "Start interval must be greater than 0" }
    }

    private var lastUsedIntervalMs: Long? = null

    override fun getInterval() =
        calculateNextInterval(lastUsedIntervalMs).also {
            updateLastUsedInterval(it)
        }

    private fun calculateNextInterval(lastUsedIntervalMs: Long?): Long {
        if (lastUsedIntervalMs == null) {
            return startInterval
        }
        return lastUsedIntervalMs * 2
    }

    private fun updateLastUsedInterval(intervalMs: Long) {
        lastUsedIntervalMs = intervalMs
    }
}
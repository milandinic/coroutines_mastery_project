package com.coroutines.project

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class RetryTest {

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `test retry count less then max`(failCount: Int) {
        runTest {
            var executionCount = 0

            val result = retry {
                if (executionCount < failCount) {
                    executionCount++
                    error("Execution count: $executionCount")
                }

                executionCount
            }

            assertEquals(result, executionCount)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [3, 4, 5])
    fun `test retry count higher then max`(failCount: Int) {
        runTest {
            assertThrows<IllegalStateException> {
                retry(retryLimit = failCount - 1) {
                    error("Out of order")
                }
            }.also {
                assertEquals("Out of order", it.message)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [10, 100])
    fun `execution lasts less then maximum allowed wait`(maximumWait: Long) {
        runTest {
            val validTimeToWait = maximumWait - 1
            val waitedTime = retry(maximalWaitTimeMs = maximumWait) {
                delay(validTimeToWait)
                validTimeToWait
            }
            Assertions.assertTrue(currentTime < maximumWait)
            assertEquals(waitedTime, validTimeToWait)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [10, 100])
    fun `execution lasts more then maximum allowed wait`(maximumWait: Long) {
        runTest {
            val illegalTimeMillis = maximumWait + 1
            assertThrows<IllegalStateException> {
                retry(maximalWaitTimeMs = maximumWait) {
                    delay(illegalTimeMillis)
                }
            }.also {
                Assertions.assertTrue(currentTime >= maximumWait)
                Assertions.assertTrue(it.message?.startsWith("Timed out after ${maximumWait}ms") == true)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [10, 50, 100])
    fun `initial delay`(initialDelay: Long) {
        runTest {
            val fakeExecutionTime: Long = 10
            retry(initialDelayMs = initialDelay) {
                delay(fakeExecutionTime)
            }
            Assertions.assertTrue(currentTime == initialDelay + 10)
        }
    }

    @Test
    fun `use jutter does not introduce a delay in case we do no retry`() {
        runTest {
            val fakeExecutionTime: Long = 10

            retry(useJitter = true, retryLimit = 2) {
                delay(fakeExecutionTime)
            }

            Assertions.assertTrue(currentTime == fakeExecutionTime)
        }
    }

    @Test
    fun `use jutter does does a delay in case we retry`() {
        runTest {
            val fakeExecutionTime: Long = 10
            var thrownOnce = false

            retry(useJitter = true) {
                delay(fakeExecutionTime)
                if (!thrownOnce) {
                    thrownOnce = true
                    error("Out of order")
                }
            }

            // fakeExecutionTime + jutter + fakeExecutionTime
            Assertions.assertTrue(currentTime > 2 * fakeExecutionTime)
        }
    }

    @Test
    fun `retry only on specific error type`() {
        val expectedResult = 666
        runTest {
            var thrownOnce = false

            val result = retry(errorType = IllegalArgumentException::class) {
                if (!thrownOnce) {
                    thrownOnce = true
                    throw IllegalArgumentException("Out of order")
                }
                expectedResult
            }
            assertEquals(expectedResult, result)
        }
    }

    @Test
    fun `do not retry on specific error type`() {
        runTest {
            var thrownOnce = false
            assertThrows<IllegalStateException> {
                retry(errorType = IllegalArgumentException::class) {
                    if (!thrownOnce) {
                        thrownOnce = true
                        throw IllegalStateException("Out of order")
                    }
                }
            }.also {
                assertEquals("Out of order", it.message)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1, 1000",
        "2, 3000",
        "3, 7000",
        "4, 15000"
    )
    fun `use exponential backoff strategy`(retryCount: Int, expectedTime: Long) {
        runTest {
            assertThrows<IllegalStateException> {
                retry(retryLimit = retryCount, backoffStrategy = BackoffStrategyExponential(1000)) {
                    error("Out of order")
                }
            }

            assertEquals(expectedTime, currentTime)
        }
    }
}
# Retry Utility

This library provides a simple `retry` suspend function that retries a suspending operation with optional backoff,
jitter, and error filtering.

## Function Signature

```kotlin
suspend fun <T> retry(
    retryLimit: Int = 3,
    initialDelayMs: Long = 0,
    backoffStrategy: BackoffStrategy = BackoffStrategyFixed(),
    useJitter: Boolean = false,
    errorType: KClass<*> = Throwable::class,
    maximalWaitTimeMs: Long = 0,
    block: suspend () -> T
): T
```

## Parameters

- **retryLimit**  
  Number of retry attempts before giving up.

- **initialDelayMs**  
  Delay before the first attempt.

- **backoffStrategy**  
  Defines how long to wait between retries.  
  Implementations should provide `getInterval()`.

- **useJitter**  
  If `true`, adds a random delay (0–1000ms) after each backoff.

- **errorType**  
  Only exceptions that are subclasses of this type will trigger a retry.  
  Other exceptions are thrown immediately.

- **maximalWaitTimeMs**  
  Maximum total time allowed for all retries.  
  If exceeded, the function throws a timeout exception.

- **block**  
  The suspending operation to execute.
-

## Basic Usage

### Simple retry with defaults

```kotlin
val result = retry {
    unreliableCall()
}
```

### Retry a specific number of times

```kotlin
val data = retry(retryLimit = 5) {
    fetchData()
}
```

### Retry only on a specific exception type

```kotlin
val response = retry(errorType = IOException::class) {
    apiCall()
}
```

### Use a custom backoff strategy

```kotlin
val output = retry(retryLimit = 4, backoffStrategy = BackoffStrategyExponential()) {
    process()
}
```

### Enable jitter up to 1000ms

```kotlin
retry(useJitter = true) {
    uploadFile()
}
```

### Enforce a maximum total duration

```kotlin
val result = retry(retryLimit = 10, maximalWaitTimeMs = 5000) {
    slowOperation()
}
```

### Example: full configuration

```kotlin
val user = retry(
    retryLimit = 5,
    initialDelayMs = 200,
    backoffStrategy = BackoffStrategyExponential(base = 100, factor = 2),
    useJitter = true,
    errorType = NetworkException::class,
    maximalWaitTimeMs = 10_000
) {
    fetchUser()
}
```
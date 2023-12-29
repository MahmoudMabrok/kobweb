Utility classes which help the user define type-safe web-worker APIs.

These classes work hand in hand with the Kobweb Worker Gradle plugin, which will look for a single implementation of the
`WorkerStrategy` class somewhere in the user's codebase.

Without these classes, web worker code would look something like this:

```kotlin
// Worker code

external val self: DedicatedWorkerGlobalScope

fun main() {
    self.onmessage = { m: MessageEvent ->
        self.postMessage("Echoed: ${m.data}")
    }
}

// Application code
```

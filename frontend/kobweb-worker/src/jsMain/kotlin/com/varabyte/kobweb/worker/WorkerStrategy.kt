package com.varabyte.kobweb.worker

import org.w3c.dom.DedicatedWorkerGlobalScope

private external val self: DedicatedWorkerGlobalScope

abstract class WorkerStrategy<I, O> {
    abstract inner class MessageConverter {
        abstract fun serializeInput(input: I): String
        abstract fun deserializeInput(input: String): I
        abstract fun serializeOutput(output: O): String
        abstract fun deserializeOutput(output: String): O
    }

    init {
        self.onmessage = { e ->
            val input = messageConverter.deserializeInput(e.data as String)
            onMessage(input)
        }
    }

    protected fun postMessage(message: O) {
        self.postMessage(messageConverter.serializeOutput(message))
    }
    abstract fun onMessage(message: I)

    abstract val messageConverter: MessageConverter
}

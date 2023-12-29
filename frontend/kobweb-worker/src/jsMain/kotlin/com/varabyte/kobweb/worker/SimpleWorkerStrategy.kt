package com.varabyte.kobweb.worker

import org.w3c.dom.DedicatedWorkerGlobalScope

/**
 * A simple implementation of [WorkerStrategy] that just uses strings as input and output.
 *
 * Type-safe worker strategies are generally more recommended, but when prototyping or for very simple use cases, this
 * can be easier to use because you don't have to worry about serialization / deserialization nor have to provide a
 * message converter.
 */
abstract class SimpleWorkerStrategy : WorkerStrategy<String, String>() {
    override val messageConverter = object : MessageConverter() {
        override fun serializeInput(input: String) = input
        override fun deserializeInput(input: String) = input
        override fun serializeOutput(output: String) = output
        override fun deserializeOutput(output: String) = output
    }
}

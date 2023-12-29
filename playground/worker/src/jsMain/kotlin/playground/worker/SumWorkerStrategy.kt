package playground.worker

import com.varabyte.kobweb.worker.WorkerStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SumParamsMessage(val a: Int, val b: Int)

@Serializable
data class SumMessage(val sum: Int)

internal class SumWorkerStrategy: WorkerStrategy<SumParamsMessage, SumMessage>() {
    override fun onMessage(message: SumParamsMessage) {
        postMessage(SumMessage(message.a + message.b))
    }

    override val messageConverter = object : MessageConverter() {
        override fun serializeInput(input: SumParamsMessage): String = Json.encodeToString(input)
        override fun deserializeInput(input: String): SumParamsMessage = Json.decodeFromString(input)
        override fun serializeOutput(output: SumMessage): String = Json.encodeToString(output)
        override fun deserializeOutput(output: String): SumMessage = Json.decodeFromString(output)
    }
}

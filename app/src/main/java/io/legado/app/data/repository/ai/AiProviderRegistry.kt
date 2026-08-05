package io.legado.app.data.repository.ai

/**
 * Registry that maps protocol identifiers to their [AiProtocolHandler] implementations.
 */
class AiProviderRegistry(handlers: List<AiProtocolHandler>) {

    private val handlerMap: Map<String, AiProtocolHandler> =
        handlers.flatMap { handler -> handler.protocols.map { it to handler } }.toMap()

    fun handlerFor(protocol: String): AiProtocolHandler {
        return handlerMap[protocol]
            ?: error("Unsupported AI protocol: $protocol")
    }
}

package com.example.superstoresimulator.domain.persistence

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Decodes a JSON array element-by-element, dropping any element that fails to decode
 * (a value that no longer satisfies a constructor invariant, e.g. an out-of-bounds
 * [com.example.superstoresimulator.domain.StaffShift], or an unresolvable registry key
 * from a renamed/removed definition) instead of failing the whole array — and by
 * extension the whole save.
 */
class TolerantListSerializer<T : Any>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(elementSerializer)
    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { jsonDecoder.json.decodeFromJsonElement(elementSerializer, element) }.getOrNull()
        }
    }
}

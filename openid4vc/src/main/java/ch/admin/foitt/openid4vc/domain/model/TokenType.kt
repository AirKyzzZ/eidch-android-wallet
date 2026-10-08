package ch.admin.foitt.openid4vc.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TokenTypeSerializer::class)
enum class TokenType(val value: String) {
    BEARER("Bearer"),
    DPOP("DPoP"),
}

object TokenTypeSerializer : KSerializer<TokenType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "TokenType",
        kind = PrimitiveKind.STRING,
    )

    override fun deserialize(decoder: Decoder): TokenType {
        val value = decoder.decodeString()
        return TokenType.entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
            ?: throw SerializationException("Unsupported token type: $value")
    }

    override fun serialize(encoder: Encoder, value: TokenType) {
        encoder.encodeString(value.value)
    }
}

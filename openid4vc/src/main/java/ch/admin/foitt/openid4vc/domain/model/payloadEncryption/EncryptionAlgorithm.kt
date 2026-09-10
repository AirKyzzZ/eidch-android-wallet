package ch.admin.foitt.openid4vc.domain.model.payloadEncryption

/**
 * The content encryption methods the wallet can decrypt, in descending order of preference. When a counterpart
 * offers several, the first entry that it also supports is the one negotiated, so the declaration order matters.
 */
enum class EncryptionAlgorithm {
    A256GCM, A128GCM
}

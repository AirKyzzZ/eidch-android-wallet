package ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase

import android.security.keystore.KeyProperties

fun interface KeySupportsPurposes {
    /**
     * Whether the keystore key stored under [keyAlias] was created with all of the given [KeyProperties] [purposes].
     * Keystore fixes a key's purposes at creation time, so a key that predates a purpose requirement has to be
     * regenerated rather than amended.
     *
     * Returns false when the key is missing or cannot be inspected.
     */
    suspend operator fun invoke(
        keyAlias: String,
        provider: String,
        purposes: Int,
    ): Boolean
}

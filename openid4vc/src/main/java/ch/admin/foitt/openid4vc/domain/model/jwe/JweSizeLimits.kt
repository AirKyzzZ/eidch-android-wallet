package ch.admin.foitt.openid4vc.domain.model.jwe

/**
 * Bounds on what a JWE may expand to while being decrypted. A compressed JWE can be far larger once inflated, so
 * these cap what a misbehaving or hostile counterpart can make the wallet allocate.
 */
data class JweSizeLimits(
    val maxCompressedCipherTextLength: Int,
    val maxDecompressedPayloadSize: Int,
) {
    companion object {
        /**
         * For credential responses. The Nimbus default of 100_000 is not enough for our credentials containing
         * large images etc., 6 mio is used for now because the max we currently receive is 5.5 mio.
         */
        val CREDENTIAL = JweSizeLimits(
            maxCompressedCipherTextLength = 6000000,
            maxDecompressedPayloadSize = 20 * 1024 * 1024,
        )

        /**
         * For the pair-wallet/v2 response. The SId spec caps `EncryptedPairWalletResponse` at 1024 characters
         * for the *whole* compact JWE, of which the cipher text is only one segment — an observed response was
         * 726 characters in total with a 430 character cipher text. Applying the same 1024 to the cipher text
         * alone is therefore stricter than the spec while still leaving room for the payload to grow.
         */
        val PAIRING = JweSizeLimits(
            maxCompressedCipherTextLength = 1024,
            maxDecompressedPayloadSize = 8 * 1024,
        )

        /**
         * No effective bound. Intended for tests whose subject is not the size limits; production callers should
         * state a real bound instead.
         */
        val UNBOUNDED = JweSizeLimits(
            maxCompressedCipherTextLength = Int.MAX_VALUE,
            maxDecompressedPayloadSize = Int.MAX_VALUE,
        )
    }
}

package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.JWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithHardwareKey
import ch.admin.foitt.openid4vc.util.assertErrorType
import ch.admin.foitt.openid4vc.util.assertOk
import com.nimbusds.jose.CompressionAlgorithm
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHEncrypter
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * On device the key agreement is delegated to the AndroidKeyStore provider. That provider does not exist on the
 * JVM, so these tests exercise the same code path against the JVM's own EC provider with a software key.
 */
class DecryptJWEWithHardwareKeyImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var decryptJWEWithHardwareKey: DecryptJWEWithHardwareKey

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        decryptJWEWithHardwareKey = DecryptJWEWithHardwareKeyImpl(
            defaultDispatcher = testDispatcher
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `A JWE matching the pair-wallet v2 parameters can be decrypted`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = true)

        val payload = decrypt(
            jweString = jwe,
            privateKey = keyPair.private,
            provider = ecProviderName,
        ).assertOk()

        assertEquals(PAYLOAD, payload)
    }

    @Test
    fun `An uncompressed JWE can be decrypted`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = false)

        val payload = decrypt(
            jweString = jwe,
            privateKey = keyPair.private,
            provider = ecProviderName,
        ).assertOk()

        assertEquals(PAYLOAD, payload)
    }

    @Test
    fun `A JWE with invalid format returns an error`() = runTest(testDispatcher) {
        decrypt(
            jweString = "invalid jwe",
            privateKey = keyPair.private,
            provider = ecProviderName,
        ).assertErrorType(JWEError.Unexpected::class)
    }

    @Test
    fun `An unavailable provider returns an error`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = true)

        decrypt(
            jweString = jwe,
            privateKey = keyPair.private,
            provider = "NoSuchProvider",
        ).assertErrorType(JWEError.Unexpected::class)
    }

    @Test
    fun `A JWE decrypted with the wrong key returns an error`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = true)

        decrypt(
            jweString = jwe,
            privateKey = createKeyPair().private,
            provider = ecProviderName,
        ).assertErrorType(JWEError.Unexpected::class)
    }

    @Test
    fun `A JWE with a header outside the Swiss Profile returns an error before it is decrypted`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = true, encryptionMethod = EncryptionMethod.A192GCM)

        decrypt(
            jweString = jwe,
            privateKey = keyPair.private,
            provider = ecProviderName,
        ).assertErrorType(JWEError.UnsupportedHeader::class)
    }

    @Test
    fun `A payload exceeding the size limit returns an error`() = runTest(testDispatcher) {
        val jwe = createJWE(compressed = true)

        decrypt(
            jweString = jwe,
            privateKey = keyPair.private,
            provider = ecProviderName,
            limits = JweSizeLimits(
                maxCompressedCipherTextLength = Int.MAX_VALUE,
                maxDecompressedPayloadSize = PAYLOAD_SIZE_IN_BYTES - 1,
            ),
        ).assertErrorType(JWEError.PayloadSizeExceeded::class)
    }

    /** Defaults to no bound so each test only states the limit it actually cares about. */
    private suspend fun decrypt(
        jweString: String,
        privateKey: PrivateKey,
        provider: String,
        limits: JweSizeLimits = JweSizeLimits.UNBOUNDED,
    ) = decryptJWEWithHardwareKey(
        jweString = jweString,
        privateKey = privateKey,
        provider = provider,
        limits = limits,
    )

    /** Mirrors the header the SId backend produces for pair-wallet/v2. */
    private fun createJWE(
        compressed: Boolean,
        encryptionMethod: EncryptionMethod = EncryptionMethod.A128GCM,
    ): String {
        val header = JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod)
            .apply { if (compressed) compressionAlgorithm(CompressionAlgorithm.DEF) }
            .contentType("application/json")
            .build()

        return JWEObject(header, Payload(PAYLOAD)).apply {
            encrypt(ECDHEncrypter(keyPair.public as ECPublicKey))
        }.serialize()
    }

    private val keyPair = createKeyPair()

    private companion object {
        const val PAYLOAD = "payload"
        val PAYLOAD_SIZE_IN_BYTES = PAYLOAD.encodeToByteArray().size

        /** The JVM provider that can do ECDH, standing in for AndroidKeyStore. */
        val ecProviderName: String = Security.getProviders("KeyAgreement.ECDH").first().name

        fun createKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    }
}

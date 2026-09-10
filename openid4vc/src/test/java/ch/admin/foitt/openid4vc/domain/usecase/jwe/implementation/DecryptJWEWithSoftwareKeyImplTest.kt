package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.JWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.payloadEncryption.EncryptionAlgorithm
import ch.admin.foitt.openid4vc.domain.usecase.jwe.CreateJWE
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithSoftwareKey
import ch.admin.foitt.openid4vc.util.assertErrorType
import ch.admin.foitt.openid4vc.util.assertOk
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.jwk.Curve.P_256
import com.nimbusds.jose.jwk.ECKey
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class DecryptJWEWithSoftwareKeyImplTest {

    private lateinit var createJWE: CreateJWE
    private lateinit var decryptJWEWithSoftwareKey: DecryptJWEWithSoftwareKey

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        createJWE = CreateJWEImpl()
        decryptJWEWithSoftwareKey = DecryptJWEWithSoftwareKeyImpl()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // Success tests are done in CreateJWEImplTest

    @Test
    fun `JWE with invalid format returns an error`() = runTest {
        val jwe = "invalid jwe"

        decryptJWEWithSoftwareKey(
            jweString = jwe,
            privateKey = keyPair.private,
            limits = JweSizeLimits.UNBOUNDED,
        ).assertErrorType(JWEError.Unexpected::class)
    }

    @Test
    fun `JWE with a header outside the Swiss Profile returns an error before it is decrypted`() = runTest {
        val jwe = createJWE(compressionAlgorithm = ZIP_VALUE, encryptionMethod = EncryptionMethod.A128CBC_HS256.name)

        decryptJWEWithSoftwareKey(
            jweString = jwe,
            privateKey = keyPair.private,
            limits = JweSizeLimits.UNBOUNDED,
        ).assertErrorType(JWEError.UnsupportedHeader::class)
    }

    @Test
    fun `Compressed JWE with decompressed payload exceeding the size limit returns an error`() = runTest {
        val jwe = createJWE(compressionAlgorithm = ZIP_VALUE)

        decryptJWEWithSoftwareKey(
            jweString = jwe,
            privateKey = keyPair.private,
            limits = JweSizeLimits(
                maxCompressedCipherTextLength = Int.MAX_VALUE,
                maxDecompressedPayloadSize = PAYLOAD_SIZE_IN_BYTES - 1,
            ),
        ).assertErrorType(JWEError.PayloadSizeExceeded::class)
    }

    @Test
    fun `Uncompressed JWE with payload exceeding the size limit returns an error`() = runTest {
        val jwe = createJWE(compressionAlgorithm = null)

        decryptJWEWithSoftwareKey(
            jweString = jwe,
            privateKey = keyPair.private,
            limits = JweSizeLimits(
                maxCompressedCipherTextLength = Int.MAX_VALUE,
                maxDecompressedPayloadSize = PAYLOAD_SIZE_IN_BYTES - 1,
            ),
        ).assertErrorType(JWEError.PayloadSizeExceeded::class)
    }

    @Test
    fun `JWE with decompressed payload exactly at the size limit can be decrypted`() = runTest {
        val jwe = createJWE(compressionAlgorithm = ZIP_VALUE)

        val decryptedPayload = decryptJWEWithSoftwareKey(
            jweString = jwe,
            privateKey = keyPair.private,
            limits = JweSizeLimits(
                maxCompressedCipherTextLength = Int.MAX_VALUE,
                maxDecompressedPayloadSize = PAYLOAD_SIZE_IN_BYTES,
            ),
        ).assertOk()

        assertEquals(PAYLOAD, decryptedPayload)
    }

    private fun createJWE(
        compressionAlgorithm: String?,
        encryptionMethod: String = ENCRYPTION_VALUE,
    ): String = createJWE(
        algorithm = ALG_VALUE,
        encryptionMethod = encryptionMethod,
        compressionAlgorithm = compressionAlgorithm,
        payload = PAYLOAD,
        encryptionKey = publicKeyJwk,
    ).assertOk()

    private val keyPair = createKeyPair()
    private val publicKey: ECKey = ECKey.Builder(P_256, keyPair.public as ECPublicKey).build()
    private val publicKeyJwk = Jwk(
        x = publicKey.x.toString(),
        y = publicKey.y.toString(),
        crv = publicKey.curve.name,
        kty = publicKey.keyType.value,
        kid = publicKey.keyID,
    )

    private fun createKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        val spec = ECGenParameterSpec("secp256r1")
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private companion object {
        const val ALG_VALUE = "ECDH-ES"
        val ENCRYPTION_VALUE = EncryptionAlgorithm.A256GCM.name
        const val ZIP_VALUE = "DEF"
        const val PAYLOAD = "payload"
        val PAYLOAD_SIZE_IN_BYTES = PAYLOAD.encodeToByteArray().size
    }
}

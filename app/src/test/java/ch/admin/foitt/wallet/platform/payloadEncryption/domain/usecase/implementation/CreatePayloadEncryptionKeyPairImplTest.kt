package ch.admin.foitt.wallet.platform.payloadEncryption.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialResponseEncryption
import ch.admin.foitt.openid4vc.domain.model.payloadEncryption.PayloadEncryptionKeyPair
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.KeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInSoftware
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.model.PayloadEncryptionError
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.usecase.CreatePayloadEncryptionKeyPair
import ch.admin.foitt.wallet.util.assertErrorType
import ch.admin.foitt.wallet.util.assertOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreatePayloadEncryptionKeyPairImplTest {
    @MockK
    private lateinit var mockCreateJWSKeyPairInSoftware: CreateJWSKeyPairInSoftware

    @MockK
    private lateinit var mockJwsKeyPair: JWSKeyPair

    private lateinit var useCase: CreatePayloadEncryptionKeyPair

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = CreatePayloadEncryptionKeyPairImpl(
            createJWSKeyPairInSoftware = mockCreateJWSKeyPairInSoftware,
        )

        coEvery { mockCreateJWSKeyPairInSoftware(SigningAlgorithm.ES256) } returns Ok(mockJwsKeyPair)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Creating payload encryption key pair returns success`() = runTest {
        val result = useCase(credentialResponseEncryption).assertOk()

        val expected = PayloadEncryptionKeyPair(
            keyPair = mockJwsKeyPair,
            alg = ALGORITHM,
            enc = ENCRYPTION,
            zip = ZIP,
        )

        assertEquals(expected, result)
    }

    @Test
    fun `Error during payload encryption key pair creation returns error`() = runTest {
        coEvery {
            mockCreateJWSKeyPairInSoftware(SigningAlgorithm.ES256)
        } returns Err(KeyPairError.IncompatibleDeviceProofKeyStorage)

        useCase(credentialResponseEncryption).assertErrorType(PayloadEncryptionError.IncompatibleDeviceProofKeyStorage::class)
    }

    @Test
    fun `An enc value the wallet cannot decrypt is skipped in favour of a supported one`() = runTest {
        val result = useCase(
            credentialResponseEncryption.copy(encValuesSupported = listOf("A256CBC-HS512", "A256GCM"))
        ).assertOk()

        assertEquals("A256GCM", result.enc)
    }

    @Test
    fun `The strongest supported enc value wins over the one the issuer lists first`() = runTest {
        val result = useCase(
            credentialResponseEncryption.copy(encValuesSupported = listOf("A128GCM", "A256GCM"))
        ).assertOk()

        assertEquals("A256GCM", result.enc)
    }

    @Test
    fun `An issuer supporting no enc value of the wallet returns an error`() = runTest {
        useCase(
            credentialResponseEncryption.copy(encValuesSupported = listOf("A256CBC-HS512"))
        ).assertErrorType(PayloadEncryptionError.Unexpected::class)
    }

    private companion object {
        const val ALGORITHM = "ECDH-ES"
        const val ENCRYPTION = "A256GCM"
        const val ZIP = "DEF"
        val credentialResponseEncryption = CredentialResponseEncryption(
            algValuesSupported = listOf(ALGORITHM),
            encValuesSupported = listOf(ENCRYPTION),
            zipValuesSupported = listOf(ZIP),
            encryptionRequired = true,
        )
    }
}

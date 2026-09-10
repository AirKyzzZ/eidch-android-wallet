package ch.admin.foitt.wallet.platform.keyPairGenerator

import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.KeySupportsPurposes
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.implementation.KeySupportsPurposesImpl
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey

@OptIn(ExperimentalCoroutinesApi::class)
class KeySupportsPurposesImplTest {

    @MockK
    private lateinit var mockKeyStore: KeyStore

    @MockK
    private lateinit var mockKeyFactory: KeyFactory

    @MockK
    private lateinit var mockPrivateKeyEntry: KeyStore.PrivateKeyEntry

    @MockK
    private lateinit var mockPrivateKey: PrivateKey

    @MockK
    private lateinit var mockKeyInfo: KeyInfo

    private lateinit var useCase: KeySupportsPurposes

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = KeySupportsPurposesImpl(
            defaultDispatcher = UnconfinedTestDispatcher(),
        )

        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance(PROVIDER) } returns mockKeyStore
        every { mockKeyStore.load(null) } just runs
        every { mockKeyStore.getEntry(KEY_ALIAS, null) } returns mockPrivateKeyEntry
        every { mockPrivateKeyEntry.privateKey } returns mockPrivateKey
        every { mockPrivateKey.algorithm } returns "EC"

        mockkStatic(KeyFactory::class)
        every { KeyFactory.getInstance("EC", PROVIDER) } returns mockKeyFactory
        every { mockKeyFactory.getKeySpec(mockPrivateKey, KeyInfo::class.java) } returns mockKeyInfo
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `A key holding exactly the requested purposes is supported`() = runTest {
        every { mockKeyInfo.purposes } returns SIGN_AND_AGREE_KEY

        assertTrue(useCase(KEY_ALIAS, PROVIDER, SIGN_AND_AGREE_KEY))
    }

    @Test
    fun `A key holding more than the requested purposes is supported`() = runTest {
        every { mockKeyInfo.purposes } returns SIGN_AND_AGREE_KEY

        assertTrue(useCase(KEY_ALIAS, PROVIDER, KeyProperties.PURPOSE_SIGN))
    }

    @Test
    fun `A key missing one of the requested purposes is not supported`() = runTest {
        every { mockKeyInfo.purposes } returns KeyProperties.PURPOSE_SIGN

        assertFalse(useCase(KEY_ALIAS, PROVIDER, SIGN_AND_AGREE_KEY))
    }

    @Test
    fun `A missing key is not supported`() = runTest {
        every { mockKeyStore.getEntry(KEY_ALIAS, null) } returns null

        assertFalse(useCase(KEY_ALIAS, PROVIDER, SIGN_AND_AGREE_KEY))
    }

    @Test
    fun `An entry that is not a private key entry is not supported`() = runTest {
        every { mockKeyStore.getEntry(KEY_ALIAS, null) } returns mockk<KeyStore.SecretKeyEntry>()

        assertFalse(useCase(KEY_ALIAS, PROVIDER, SIGN_AND_AGREE_KEY))
    }

    @Test
    fun `A key that cannot be inspected is not supported`() = runTest {
        every {
            mockKeyFactory.getKeySpec(mockPrivateKey, KeyInfo::class.java)
        } throws IllegalArgumentException("not a keystore key")

        assertFalse(useCase(KEY_ALIAS, PROVIDER, SIGN_AND_AGREE_KEY))
    }

    private companion object {
        const val KEY_ALIAS = "clientAttestation"
        const val PROVIDER = "AndroidKeyStore"
        const val SIGN_AND_AGREE_KEY = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_AGREE_KEY
    }
}

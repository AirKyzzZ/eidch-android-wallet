package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.UnsupportedJweHeaderException
import com.nimbusds.jose.CompressionAlgorithm
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.util.Base64URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class JweHeaderValidationTest {

    @Test
    fun `The profile the SId and issuer backends use is accepted`() {
        val ephemeralPublicKey = header().checkSwissProfile()

        assertEquals(p256Key, ephemeralPublicKey)
    }

    @Test
    fun `A128GCM is accepted for as long as it is not confirmed that all backends use A256GCM`() {
        header(encryptionMethod = EncryptionMethod.A128GCM).checkSwissProfile()
    }

    @Test
    fun `An uncompressed JWE is accepted`() {
        header(compressionAlgorithm = null).checkSwissProfile()
    }

    @Test
    fun `An algorithm other than ECDH-ES is rejected`() {
        listOf(
            JWEAlgorithm.ECDH_ES_A128KW,
            JWEAlgorithm.ECDH_ES_A192KW,
            JWEAlgorithm.ECDH_ES_A256KW,
            JWEAlgorithm.RSA_OAEP_256,
        ).forEach { algorithm ->
            val exception = assertThrows<UnsupportedJweHeaderException> {
                header(algorithm = algorithm).checkSwissProfile()
            }

            assertEquals("unsupported alg: $algorithm", exception.message)
        }
    }

    @Test
    fun `An encryption method other than A256GCM or A128GCM is rejected`() {
        listOf(
            EncryptionMethod.A192GCM,
            EncryptionMethod.A128CBC_HS256,
            EncryptionMethod.A192CBC_HS384,
            EncryptionMethod.A256CBC_HS512,
            EncryptionMethod.XC20P,
        ).forEach { encryptionMethod ->
            val exception = assertThrows<UnsupportedJweHeaderException> {
                header(encryptionMethod = encryptionMethod).checkSwissProfile()
            }

            assertEquals("unsupported enc: $encryptionMethod", exception.message)
        }
    }

    @Test
    fun `A compression algorithm other than deflate is rejected`() {
        val compressionAlgorithm = CompressionAlgorithm("GZIP")

        val exception = assertThrows<UnsupportedJweHeaderException> {
            header(compressionAlgorithm = compressionAlgorithm).checkSwissProfile()
        }

        assertEquals("unsupported zip: $compressionAlgorithm", exception.message)
    }

    @Test
    fun `A missing ephemeral public key is rejected`() {
        val exception = assertThrows<UnsupportedJweHeaderException> {
            header(ephemeralPublicKey = null).checkSwissProfile()
        }

        assertEquals("missing or non-EC ephemeral public key", exception.message)
    }

    @Test
    fun `An ephemeral public key that is not an EC key is rejected`() {
        val exception = assertThrows<UnsupportedJweHeaderException> {
            header(ephemeralPublicKey = octetKey).checkSwissProfile()
        }

        assertEquals("missing or non-EC ephemeral public key", exception.message)
    }

    @Test
    fun `An ephemeral public key on a curve other than P-256 is rejected`() {
        val exception = assertThrows<UnsupportedJweHeaderException> {
            header(ephemeralPublicKey = p384Key).checkSwissProfile()
        }

        assertEquals("unsupported ephemeral key curve: ${Curve.P_384}", exception.message)
    }

    private fun header(
        algorithm: JWEAlgorithm = JWEAlgorithm.ECDH_ES,
        encryptionMethod: EncryptionMethod = EncryptionMethod.A256GCM,
        compressionAlgorithm: CompressionAlgorithm? = CompressionAlgorithm.DEF,
        ephemeralPublicKey: JWK? = p256Key,
    ): JWEHeader = JWEHeader.Builder(algorithm, encryptionMethod)
        .apply {
            compressionAlgorithm?.let { compressionAlgorithm(it) }
            ephemeralPublicKey?.let { ephemeralPublicKey(it) }
        }
        .build()

    private companion object {
        val p256Key = createEcKey(Curve.P_256, "secp256r1")
        val p384Key = createEcKey(Curve.P_384, "secp384r1")
        val octetKey: JWK = OctetKeyPair.Builder(Curve.X25519, Base64URL.encode(ByteArray(32))).build()

        fun createEcKey(curve: Curve, standardName: String): ECKey {
            val keyPair = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec(standardName))
            }.generateKeyPair()

            return ECKey.Builder(curve, keyPair.public as ECPublicKey).build()
        }
    }
}

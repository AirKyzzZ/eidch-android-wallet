package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerificationMethod
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaDocumentRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.VerifyVeranaDidKeyBinding
import com.nimbusds.jose.util.Base64URL
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

class VerifyVeranaDidKeyBindingImpl @Inject constructor(
    private val documentRepository: VeranaDocumentRepository,
) : VerifyVeranaDidKeyBinding {
    override suspend fun invoke(
        did: String,
        role: VeranaTrustRole,
        certificateKey: Jwk,
    ): VeranaDidKeyBinding {
        val document = documentRepository.fetchDidDocument(did) ?: return VeranaDidKeyBinding.UNAVAILABLE
        // The devnet verifier lists its request-signing key under authentication only, not assertionMethod.
        val methods = when (role) {
            VeranaTrustRole.ISSUER -> document.assertionMethods
            VeranaTrustRole.VERIFIER -> document.assertionMethods + document.authenticationMethods
        }
        return if (methods.any { it.hasP256Key(certificateKey) }) {
            VeranaDidKeyBinding.PROVEN
        } else {
            VeranaDidKeyBinding.NOT_PROVEN
        }
    }

    private fun VeranaVerificationMethod.hasP256Key(key: Jwk): Boolean {
        val jwk = publicKeyJwk ?: return false
        fun field(name: String) = (jwk[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val presentedIsP256 = key.kty == EC && key.crv == P_256
        val publishedIsP256 = field("kty") == EC && field("crv") == P_256
        if (!presentedIsP256 || !publishedIsP256) return false
        return sameCoordinate(field("x"), key.x) && sameCoordinate(field("y"), key.y)
    }

    private fun sameCoordinate(published: String?, presented: String?): Boolean {
        if (published == null || presented == null) return false
        val publishedBytes = runCatching { Base64URL(published).decode() }.getOrNull() ?: return false
        val presentedBytes = runCatching { Base64URL(presented).decode() }.getOrNull() ?: return false
        return publishedBytes.isNotEmpty() && publishedBytes.contentEquals(presentedBytes)
    }

    private companion object {
        const val EC = "EC"
        const val P_256 = "P-256"
    }
}

package ch.admin.foitt.openid4vc.domain.model.vcSdJwt

import ch.admin.foitt.openid4vc.domain.model.anycredential.toBusinessExpiryInstant
import ch.admin.foitt.openid4vc.domain.model.anycredential.toInstant
import ch.admin.foitt.openid4vc.domain.model.claimsPathPointer.ClaimsPathPointerComponent
import ch.admin.foitt.openid4vc.domain.model.claimsPathPointer.toPointerString
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.sdjwt.SdJwt
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.JWTClaimNames
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Instant

/**
 * https://datatracker.ietf.org/doc/html/draft-ietf-oauth-sd-jwt-vc
 */
open class VcSdJwt(
    rawVcSdJwt: String
) : SdJwt(rawSdJwt = rawVcSdJwt, nonSelectivelyDisclosableClaims = NON_SELECTIVELY_DISCLOSABLE_CLAIMS) {

    private val x5cCertificate: X509Certificate? = signedJwt.header.x509CertChain?.firstOrNull()?.let { encodedCertificate ->
        X509CertUtils.parseWithException(encodedCertificate.decode()).also(X509Certificate::checkValidity)
    }
    val x5cIssuerDid: String? = x5cCertificate?.let(::getIssuerDid)
    val x5cPublicKey: Jwk? = x5cCertificate?.let(::getPublicKey)
    val x5cDnsNames: Set<String> = x5cCertificate?.let(::getDnsNames).orEmpty()
    val isX5cIssuerKey: Boolean = keyId == null
    val kid: String = keyId ?: x5cIssuerDid ?: error("VcSdJwt is missing a DID-bound issuer key")

    init {
        val nonSelectivelyDisclosableClaims = payloadJson.keys - NON_SELECTIVELY_DISCLOSABLE_CLAIMS
        check(nonSelectivelyDisclosableClaims.isEmpty()) {
            "VcSdJwt contains unregistered non-selectively disclosable claims"
        }
    }

    val vct = processedJson.jsonObject[CLAIM_KEY_VCT]?.jsonPrimitive?.content ?: error("missing vct claim")
    val vctIntegrity: String? = processedJson.jsonObject[CLAIM_KEY_VCT_INTEGRITY]?.jsonPrimitive?.content
    val vctMetadataUri: String? = processedJson.jsonObject[CLAIM_KEY_VCT_METADATA_URI]?.jsonPrimitive?.content
    val vctMetadataUriIntegrity: String? = processedJson.jsonObject[CLAIM_KEY_VCT_METADATA_URI_INTEGRITY]?.jsonPrimitive?.content
    val credentialSchemaId: String? = processedJson.jsonObject[CLAIM_KEY_CREDENTIAL_SCHEMA]
        ?.jsonObject
        ?.get(CLAIM_KEY_CREDENTIAL_ID)
        ?.jsonPrimitive
        ?.content

    /**
     * Resolves the key binding JWK by decoding the `cnf` claim, supporting both the standard and legacy structures.
     *
     * Two CNF structures are intentionally supported:
     * - the standard `cnf.jwk`, where the JWK is nested under the `jwk` key;
     * - the legacy flat `cnf`, where the JWK fields are held directly.
     *
     * Keeping both decoding paths is an accepted business decision for the time being. Some early production
     * credentials were released with the flat CNF format and we decided to keep managing them to not break usage
     * until further notice.
     */
    val cnfJwk = processedJson.jsonObject[CLAIM_KEY_CNF]?.jsonObject[CLAIM_KEY_CNF_JWK]
        ?: processedJson.jsonObject[CLAIM_KEY_CNF]
    val status = processedJson.jsonObject[CLAIM_KEY_STATUS]

    /* expiry_date claim can optionally be put in disclosures, so it has to be read here */
    val businessExpiryDate: Instant? = runSuspendCatching {
        processedJson.jsonObject[CLAIM_KEY_BUSINESS_EXPIRY_DATE]?.jsonPrimitive?.content
    }.get()?.toBusinessExpiryInstant()

    /* "sub" claim can optionally be put in disclosures, so it has to be read here */
    override val subject: String? = runSuspendCatching {
        processedJson.jsonObject[JWTClaimNames.SUBJECT]?.jsonPrimitive?.content
    }.get()

    /* "iat" claim can optionally be put in disclosures, so it has to be read here */
    override val issuedAt: Instant? = runSuspendCatching {
        processedJson.jsonObject[JWTClaimNames.ISSUED_AT]?.jsonPrimitive?.content
    }.get()?.toInstant()

    private fun getIssuerDid(certificate: X509Certificate): String = certificate.subjectAlternativeNames.orEmpty()
        .mapNotNull { alternativeName ->
            val type = alternativeName.getOrNull(0) as? Int
            val value = alternativeName.getOrNull(1) as? String
            value?.takeIf { type == URI_SUBJECT_ALTERNATIVE_NAME && it.startsWith(DID_PREFIX) }
        }
        .singleOrNull()
        ?: error("x5c certificate must contain exactly one DID URI subject alternative name")

    private fun getPublicKey(certificate: X509Certificate): Jwk {
        val publicKey = certificate.publicKey as? ECPublicKey ?: error("x5c certificate must contain an EC public key")
        val curve = Curve.forECParameterSpec(publicKey.params)
        check(curve == Curve.P_256) { "x5c certificate must use P-256" }
        val key = ECKey.Builder(curve, publicKey).build()
        return Jwk(
            x = key.x.toString(),
            y = key.y.toString(),
            crv = key.curve.name,
            kty = key.keyType.value,
        )
    }

    private fun getDnsNames(certificate: X509Certificate): Set<String> = certificate.subjectAlternativeNames.orEmpty()
        .mapNotNull { alternativeName ->
            val type = alternativeName.getOrNull(0) as? Int
            val value = alternativeName.getOrNull(1) as? String
            value?.takeIf { type == DNS_SUBJECT_ALTERNATIVE_NAME }
        }
        .toSet()

    companion object {
        private const val URI_SUBJECT_ALTERNATIVE_NAME = 6
        private const val DNS_SUBJECT_ALTERNATIVE_NAME = 2
        private const val DID_PREFIX = "did:"
        private const val CLAIM_KEY_CNF = "cnf"
        private const val CLAIM_KEY_CNF_JWK = "jwk"
        private const val CLAIM_KEY_VCT = "vct"
        private const val CLAIM_KEY_VCT_INTEGRITY = "vct#integrity"
        private const val CLAIM_KEY_VCT_METADATA_URI = "vct_metadata_uri"
        private const val CLAIM_KEY_VCT_METADATA_URI_INTEGRITY = "vct_metadata_uri#integrity"
        private const val CLAIM_KEY_STATUS = "status"
        private const val CLAIM_KEY_BUSINESS_EXPIRY_DATE = "expiry_date"
        private const val CLAIM_KEY_CREDENTIAL_ID = "id"
        private const val CLAIM_KEY_CREDENTIAL_SCHEMA = "credentialSchema"

        // See https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-15.html#section-3.2.2.2
        val NON_SELECTIVELY_DISCLOSABLE_CLAIMS = setOf(
            "iss", "nbf", "exp", "iat", CLAIM_KEY_CNF, CLAIM_KEY_STATUS,
            "_sd", "_sd_alg",
            CLAIM_KEY_VCT, CLAIM_KEY_VCT_INTEGRITY, CLAIM_KEY_VCT_METADATA_URI, CLAIM_KEY_VCT_METADATA_URI_INTEGRITY,
            CLAIM_KEY_CREDENTIAL_ID,
            CLAIM_KEY_CREDENTIAL_SCHEMA,
        )

        val BUSINESS_EXPIRY_DATE_CLAIM_PATH = listOf(
            ClaimsPathPointerComponent.String(CLAIM_KEY_BUSINESS_EXPIRY_DATE)
        ).toPointerString()
    }
}

package ch.admin.foitt.wallet.platform.invitation.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.CredentialOffer
import ch.admin.foitt.wallet.platform.invitation.domain.model.GetCredentialOfferError
import ch.admin.foitt.wallet.platform.invitation.domain.model.InvitationError
import ch.admin.foitt.wallet.platform.invitation.domain.model.toGetCredentialOfferError
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.FetchCredentialOfferByReference
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.GetCredentialOfferFromUri
import ch.admin.foitt.wallet.platform.utils.JsonParsingError
import ch.admin.foitt.wallet.platform.utils.SafeJson
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIf
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

internal class GetCredentialOfferFromUriImpl @Inject constructor(
    private val safeJson: SafeJson,
    private val fetchCredentialOfferByReference: FetchCredentialOfferByReference,
) : GetCredentialOfferFromUri {
    override suspend fun invoke(uri: URI): Result<CredentialOffer, GetCredentialOfferError> = coroutineBinding {
        val parameters = runSuspendCatching {
            uri.credentialOfferParameters()
        }.mapError { throwable ->
            throwable.toGetCredentialOfferError("GetCredentialOfferFromUri error")
        }.bind()

        val inlineOffer = parameters[CREDENTIAL_OFFER]
        val referencedOffer = parameters[CREDENTIAL_OFFER_URI]
        if ((inlineOffer == null) == (referencedOffer == null)) {
            Err(
                InvitationError.CredentialOfferDeserializationFailed(
                    IllegalArgumentException("exactly one credential offer parameter is required")
                )
            ).bind<CredentialOffer>()
        }

        val jsonString = inlineOffer ?: runSuspendCatching {
            URI(checkNotNull(referencedOffer)).also { reference ->
                require(reference.scheme == HTTPS_SCHEME)
                require(!reference.host.isNullOrBlank())
                require(reference.userInfo == null)
                require(reference.fragment == null)
            }
        }.mapError { throwable ->
            throwable.toGetCredentialOfferError("Invalid credential_offer_uri")
        }.bind().let { reference ->
            fetchCredentialOfferByReference(reference).bind()
        }

        safeJson.safeDecodeStringTo<CredentialOffer>(
            string = jsonString,
        ).mapError(JsonParsingError::toGetCredentialOfferError)
            .bind()
    }.toErrorIf(predicate = { it.grants.preAuthorizedCode == null }) {
        InvitationError.UnsupportedGrantType("Unsupported grant type: ${it.grants}")
    }.toErrorIf(predicate = { it.credentialConfigurationIds.isEmpty() }) {
        InvitationError.NoCredentialsFound
    }

    private fun URI.credentialOfferParameters(): Map<String, String> {
        val raw = requireNotNull(rawQuery)
        val components = if ('=' in raw) {
            raw.split('&')
        } else {
            listOf(URLDecoder.decode(raw, StandardCharsets.UTF_8))
        }
        val parameters = components.map { component ->
            val separator = component.indexOf('=')
            require(separator > 0)
            val name = URLDecoder.decode(component.substring(0, separator), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(component.substring(separator + 1), StandardCharsets.UTF_8)
            name to value
        }
        require(parameters.map { it.first }.distinct().size == parameters.size)
        return parameters.toMap()
    }

    private companion object {
        const val CREDENTIAL_OFFER = "credential_offer"
        const val CREDENTIAL_OFFER_URI = "credential_offer_uri"
        const val HTTPS_SCHEME = "https"
    }
}

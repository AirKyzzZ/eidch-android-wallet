package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase

import ch.admin.foitt.openid4vc.domain.model.presentationRequest.AuthorizationRequest
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.FetchAndCacheVerifierDisplayDataError
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.VerificationProcessType
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerifierTrustContext
import com.github.michaelbull.result.Result

interface FetchAndCacheVerifierDisplayData {
    suspend operator fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
    ): Result<Unit, FetchAndCacheVerifierDisplayDataError> = invoke(
        authorizationRequest = authorizationRequest,
        verificationProcessType = verificationProcessType,
        verifierAttestationTrusted = verifierAttestationTrusted,
        veranaTrustContext = null,
    )

    suspend operator fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
        veranaTrustContext: VeranaVerifierTrustContext?,
    ): Result<Unit, FetchAndCacheVerifierDisplayDataError>
}

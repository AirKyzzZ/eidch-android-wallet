package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.presentationRequest.AuthorizationRequest
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.ClientMetaData
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwt
import ch.admin.foitt.wallet.platform.actorEnvironment.domain.model.ActorEnvironment
import ch.admin.foitt.wallet.platform.actorEnvironment.domain.usecase.GetActorEnvironment
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorField
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorType
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.FetchAndCacheVerifierDisplayDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.FetchTrustForVerificationError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.toFetchAndCacheVerifierDisplayDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.ActorUpdateGate
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.FetchAndCacheVerifierDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.FetchTrustForVerification
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.InitializeActorForScope
import ch.admin.foitt.wallet.platform.credential.domain.usecase.GetAllAnyCredentialsByCredentialId
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.VerificationProcessType
import ch.admin.foitt.wallet.platform.navigation.domain.model.ComponentScope
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.ActorComplianceState
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.IdentityTrustStatement
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.NonComplianceReasonDisplay
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustStatus
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.VcSchemaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerifierTrustContext
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import timber.log.Timber
import uniffi.heidi_dcql_rust.Meta
import javax.inject.Inject

internal class FetchAndCacheVerifierDisplayDataImpl @Inject constructor(
    private val getActorEnvironment: GetActorEnvironment,
    private val fetchTrustForVerification: FetchTrustForVerification,
    private val initializeActorForScope: InitializeActorForScope,
    private val getAllAnyCredentialsByCredentialId: GetAllAnyCredentialsByCredentialId,
    private val evaluateVeranaTrust: EvaluateVeranaTrust,
    private val actorUpdateGate: ActorUpdateGate,
) : FetchAndCacheVerifierDisplayData {
    override suspend fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
        veranaTrustContext: VeranaVerifierTrustContext?,
    ): Result<Unit, FetchAndCacheVerifierDisplayDataError> = coroutineBinding {
        val actorUpdateGeneration = actorUpdateGate.begin(ComponentScope.Verifier)
        val verifierNameDisplay = authorizationRequest.clientMetaData?.toVerifierName()
        val verifierLogoDisplay = authorizationRequest.clientMetaData?.toVerifierLogo()

        if (verificationProcessType == VerificationProcessType.PROXIMITY) {
            cacheProximityVerifierFromMetadata(
                verifierNameDisplay = verifierNameDisplay,
                verifierLogoDisplay = verifierLogoDisplay,
                verifierAttestationTrusted = verifierAttestationTrusted,
                actorUpdateGeneration = actorUpdateGeneration,
            )
            return@coroutineBinding Unit
        }
        val verifierDid = authorizationRequest.clientIdentifier.clientId
        val environment = getActorEnvironment(verifierDid)
        val trustCheckResult = fetchTrustForVerification(authorizationRequest)
            .mapError(FetchTrustForVerificationError::toFetchAndCacheVerifierDisplayDataError)
            .bind()

        val trustStatement = trustCheckResult.identityTrustStatement

        Timber.d("${trustStatement ?: "trust statement not evaluated or failed"}")

        val trustStatus = getTrustStatus(
            environment = environment,
            trustStatement = trustStatement
        )

        val vcSchemaTrustStatus = trustCheckResult.vcSchemaTrustStatus

        val verifierTrustNameDisplay: List<ActorField<String>>? =
            trustStatement?.entityName?.toActorField() // trust statement available -> use it without metadata as default
                ?: verifierNameDisplay // trust statement not available -> use metadata
        val verifierTrustLogoDisplay: List<ActorField<String>>? = verifierLogoDisplay

        val nonComplianceData = trustCheckResult.nonComplianceData
        val nonComplianceReason: List<ActorField<String>>? = nonComplianceData.reasonDisplays?.toNonComplianceReason()
        val veranaTrustEvidence = evaluateVeranaTrust(veranaTrustContext, authorizationRequest)

        val presentationVerifierDisplay = ActorDisplayData(
            name = verifierTrustNameDisplay,
            image = verifierTrustLogoDisplay,
            trustStatus = trustStatus,
            vcSchemaTrustStatus = vcSchemaTrustStatus,
            preferredLanguage = null,
            actorType = ActorType.VERIFIER,
            actorComplianceState = nonComplianceData.state,
            nonComplianceReason = nonComplianceReason,
            veranaTrustEvidence = veranaTrustEvidence,
        )

        actorUpdateGate.publishIfCurrent(
            componentScope = ComponentScope.Verifier,
            generation = actorUpdateGeneration,
        ) {
            initializeActorForScope(
                actorDisplayData = presentationVerifierDisplay,
                componentScope = ComponentScope.Verifier,
            )
        }
    }

    private suspend fun cacheProximityVerifierFromMetadata(
        verifierNameDisplay: List<ActorField<String>>?,
        verifierLogoDisplay: List<ActorField<String>>?,
        verifierAttestationTrusted: Boolean?,
        actorUpdateGeneration: Long,
    ) {
        val trustStatus = if (verifierAttestationTrusted == true) {
            TrustStatus.TRUSTED_PROXIMITY_VERIFIER
        } else {
            TrustStatus.NOT_TRUSTED_PROXIMITY_VERIFIER
        }

        val vcSchemaTrustStatus = if (verifierAttestationTrusted == true) {
            VcSchemaTrustStatus.TRUSTED
        } else {
            VcSchemaTrustStatus.NOT_TRUSTED
        }

        actorUpdateGate.publishIfCurrent(
            componentScope = ComponentScope.Verifier,
            generation = actorUpdateGeneration,
        ) {
            initializeActorForScope(
                actorDisplayData = ActorDisplayData(
                    name = verifierNameDisplay,
                    image = verifierLogoDisplay,
                    trustStatus = trustStatus,
                    vcSchemaTrustStatus = vcSchemaTrustStatus,
                    preferredLanguage = null,
                    actorType = ActorType.VERIFIER,
                    actorComplianceState = ActorComplianceState.UNKNOWN,
                    nonComplianceReason = null,
                    veranaTrustEvidence = null,
                ),
                componentScope = ComponentScope.Verifier,
            )
        }
    }

    private suspend fun evaluateVeranaTrust(
        context: VeranaVerifierTrustContext?,
        authorizationRequest: AuthorizationRequest,
    ): VeranaTrustEvidence? {
        context ?: return null
        val verifierDid = context.authenticatedVerifierDid.takeIf { it.startsWith(DID_PREFIX) } ?: return null

        return evaluateVeranaTrust(
            role = VeranaTrustRole.VERIFIER,
            did = verifierDid,
            vct = requestedVct(authorizationRequest, context.credentialId),
            certificateKey = context.certificateKey,
        )
    }

    private suspend fun requestedVct(authorizationRequest: AuthorizationRequest, credentialId: Long): String? {
        val requestedVcts = authorizationRequest.dcqlQuery?.credentials.orEmpty()
            .flatMap { credentialQuery -> (credentialQuery.meta as? Meta.SdjwtVc)?.vctValues.orEmpty() }
            .distinct()
        requestedVcts.singleOrNull()?.let { return it }

        val sharedVcts = getAllAnyCredentialsByCredentialId(credentialId).get().orEmpty().mapNotNull { (it as? VcSdJwt)?.vct }
        return sharedVcts.firstOrNull { it in requestedVcts }
    }

    private fun ClientMetaData.toVerifierName(): List<ActorField<String>> = clientNameList.map { entry ->
        ActorField(
            value = entry.clientName,
            locale = entry.locale,
        )
    }

    private fun ClientMetaData.toVerifierLogo(): List<ActorField<String>> = logoUriList.map { entry ->
        ActorField(
            value = entry.logoUri,
            locale = entry.locale,
        )
    }

    private fun getTrustStatus(
        environment: ActorEnvironment,
        trustStatement: IdentityTrustStatement?
    ) = when (environment) {
        ActorEnvironment.PRODUCTION, ActorEnvironment.BETA -> {
            if (trustStatement != null) {
                TrustStatus.TRUSTED
            } else {
                TrustStatus.NOT_TRUSTED
            }
        }

        ActorEnvironment.EXTERNAL -> TrustStatus.EXTERNAL
    }

    private fun <T> Map<String, T>.toActorField(): List<ActorField<T>> = map { entry ->
        ActorField(
            value = entry.value,
            locale = entry.key,
        )
    }

    private fun List<NonComplianceReasonDisplay>.toNonComplianceReason(): List<ActorField<String>> = map { entry ->
        ActorField(
            value = entry.reason,
            locale = entry.locale,
        )
    }

    private companion object {
        const val DID_PREFIX = "did:"
    }
}

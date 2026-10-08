package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase

import ch.admin.foitt.wallet.platform.credential.domain.model.AnyIssuerDisplay
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.NonComplianceData
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustCheckResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence

interface CacheIssuerDisplayData {
    suspend operator fun invoke(
        trustCheckResult: TrustCheckResult?,
        issuerDisplays: List<AnyIssuerDisplay>,
        nonComplianceData: NonComplianceData,
    ) = invoke(
        trustCheckResult = trustCheckResult,
        issuerDisplays = issuerDisplays,
        nonComplianceData = nonComplianceData,
        veranaTrustEvidence = null,
    )

    suspend operator fun invoke(
        trustCheckResult: TrustCheckResult?,
        issuerDisplays: List<AnyIssuerDisplay>,
        nonComplianceData: NonComplianceData,
        veranaTrustEvidence: VeranaTrustEvidence?,
    )
}

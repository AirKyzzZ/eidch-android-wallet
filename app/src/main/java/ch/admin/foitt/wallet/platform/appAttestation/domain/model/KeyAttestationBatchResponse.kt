package ch.admin.foitt.wallet.platform.appAttestation.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeyAttestationBatchResponse(
    @SerialName("keyAttestations")
    val keyAttestations: List<KeyAttestationResponseItem>,
)

@Serializable
data class KeyAttestationResponseItem(
    @SerialName("id")
    val id: Int,
    @SerialName("keyAttestation")
    val keyAttestation: KeyAttestationJwt,
)

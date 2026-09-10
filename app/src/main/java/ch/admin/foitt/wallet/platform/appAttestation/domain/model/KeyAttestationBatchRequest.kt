package ch.admin.foitt.wallet.platform.appAttestation.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class KeyAttestationBatchRequest(
    @SerialName("keys")
    val keys: List<KeyAttestationRequestItem>,
)

@Serializable
internal data class KeyAttestationRequestItem(
    @SerialName("id")
    val id: Int,
    @SerialName("cnf")
    val cnf: Confirmation,
)

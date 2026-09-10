package ch.admin.foitt.wallet.platform.credential.domain.model

import ch.admin.foitt.wallet.platform.actorEnvironment.domain.model.ActorEnvironment
import ch.admin.foitt.wallet.platform.credentialStatus.domain.model.CredentialDisplayStatus
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.VerifiableProgressionState

data class CredentialDisplayData(
    val credentialId: Long,
    val title: String?,
    val subtitle: String?,
    val status: CredentialDisplayStatus,
    val logo: ByteArray?,
    val backgroundColor: String?,
    val progressionState: VerifiableProgressionState,
    val actorEnvironment: ActorEnvironment,
    val createdAt: Long,
) {
    constructor(
        credentialId: Long,
        status: CredentialDisplayStatus,
        credentialDisplay: CredentialDisplay,
        progressionState: VerifiableProgressionState,
        actorEnvironment: ActorEnvironment,
        createdAt: Long,
    ) : this(
        credentialId = credentialId,
        status = status,
        title = credentialDisplay.entity.name,
        subtitle = credentialDisplay.entity.description,
        logo = credentialDisplay.logo?.image,
        backgroundColor = credentialDisplay.entity.backgroundColor,
        progressionState = progressionState,
        actorEnvironment = actorEnvironment,
        createdAt = createdAt,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CredentialDisplayData

        if (credentialId != other.credentialId) return false
        if (createdAt != other.createdAt) return false
        if (title != other.title) return false
        if (subtitle != other.subtitle) return false
        if (status != other.status) return false
        if (!logo.contentEquals(other.logo)) return false
        if (backgroundColor != other.backgroundColor) return false
        if (progressionState != other.progressionState) return false
        if (actorEnvironment != other.actorEnvironment) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (subtitle?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (logo?.contentHashCode() ?: 0)
        result = 31 * result + (backgroundColor?.hashCode() ?: 0)
        result = 31 * result + progressionState.hashCode()
        result = 31 * result + actorEnvironment.hashCode()
        return result
    }
}

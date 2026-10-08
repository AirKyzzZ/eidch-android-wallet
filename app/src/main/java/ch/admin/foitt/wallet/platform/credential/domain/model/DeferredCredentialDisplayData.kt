package ch.admin.foitt.wallet.platform.credential.domain.model

import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.DeferredProgressionState

data class DeferredCredentialDisplayData(
    val credentialId: Long,
    val title: String?,
    val status: DeferredProgressionState,
    val logo: ByteArray?,
    val backgroundColor: String?,
    val createdAt: Long,
) {
    constructor(
        credentialId: Long,
        status: DeferredProgressionState,
        credentialDisplay: CredentialDisplay,
        createdAt: Long,
    ) : this(
        credentialId = credentialId,
        title = credentialDisplay.entity.name,
        status = status,
        logo = credentialDisplay.logo?.image,
        backgroundColor = credentialDisplay.entity.backgroundColor,
        createdAt = createdAt,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeferredCredentialDisplayData

        if (credentialId != other.credentialId) return false
        if (createdAt != other.createdAt) return false
        if (title != other.title) return false
        if (status != other.status) return false
        if (!logo.contentEquals(other.logo)) return false
        if (backgroundColor != other.backgroundColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (logo?.contentHashCode() ?: 0)
        result = 31 * result + (backgroundColor?.hashCode() ?: 0)
        return result
    }
}

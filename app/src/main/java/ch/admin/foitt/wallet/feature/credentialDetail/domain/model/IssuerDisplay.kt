package ch.admin.foitt.wallet.feature.credentialDetail.domain.model

import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.LocalizedDisplay

data class IssuerDisplay(
    val name: String?,
    val image: ByteArray? = null,
    val imageAltText: String? = null,
    override val locale: String
) : LocalizedDisplay {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IssuerDisplay

        if (name != other.name) return false
        if (!image.contentEquals(other.image)) return false
        if (imageAltText != other.imageAltText) return false
        if (locale != other.locale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (image?.contentHashCode() ?: 0)
        result = 31 * result + (imageAltText?.hashCode() ?: 0)
        result = 31 * result + locale.hashCode()
        return result
    }
}

fun CredentialIssuerDisplay.toIssuerDisplay() = IssuerDisplay(
    name = entity.name,
    image = image?.image,
    imageAltText = entity.imageAltText,
    locale = entity.locale,
)

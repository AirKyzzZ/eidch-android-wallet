package ch.admin.foitt.wallet.platform.credential.domain.model

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.OidIssuerDisplay

data class AnyIssuerDisplay(
    override val locale: String? = null,
    override val name: String? = null,
    val logo: String? = null,
    val logoImageData: ByteArray? = null,
    val logoAltText: String? = null,
) : AnyDisplay {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnyIssuerDisplay

        if (locale != other.locale) return false
        if (name != other.name) return false
        if (logo != other.logo) return false
        if (!logoImageData.contentEquals(other.logoImageData)) return false
        if (logoAltText != other.logoAltText) return false

        return true
    }

    override fun hashCode(): Int {
        var result = locale?.hashCode() ?: 0
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (logo?.hashCode() ?: 0)
        result = 31 * result + (logoImageData?.contentHashCode() ?: 0)
        result = 31 * result + (logoAltText?.hashCode() ?: 0)
        return result
    }
}

fun OidIssuerDisplay.toAnyIssuerDisplay() =
    AnyIssuerDisplay(
        locale = locale,
        name = name,
        logo = logo?.uri,
        logoAltText = logo?.altText,
    )

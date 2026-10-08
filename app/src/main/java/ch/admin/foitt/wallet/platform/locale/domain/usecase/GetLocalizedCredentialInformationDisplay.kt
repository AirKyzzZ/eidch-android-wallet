package ch.admin.foitt.wallet.platform.locale.domain.usecase

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialInformationDisplay

interface GetLocalizedCredentialInformationDisplay {
    operator fun <T : CredentialInformationDisplay> invoke(
        displays: List<T>,
        preferredLocaleString: String? = null,
    ): T?
}

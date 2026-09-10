package ch.admin.foitt.wallet.feature.credentialDetail.mock

import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplayEntity

object MockIssuerDisplays {

    const val CREDENTIAL_ID = 5L

    val issuerDisplayEntity1 = CredentialIssuerDisplayEntity(
        id = 33,
        credentialId = CREDENTIAL_ID,
        locale = "locale1",
        name = "name1",
    )

    val issuerDisplay1 = CredentialIssuerDisplay(
        entity = issuerDisplayEntity1,
        image = null,
    )

    private val issuerDisplayEntity2 = CredentialIssuerDisplayEntity(
        id = 34,
        credentialId = CREDENTIAL_ID,
        locale = "locale2",
        name = "name2",
    )

    val issuerDisplay2 = CredentialIssuerDisplay(
        entity = issuerDisplayEntity2,
        image = null,
    )

    val issuerDisplays = listOf(issuerDisplay1, issuerDisplay2)
}

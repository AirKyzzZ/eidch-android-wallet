package ch.admin.foitt.wallet.platform.appAttestation.domain.model

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk

internal data class AttestableKey(
    val keyPair: JWSKeyPair,
    val jwk: Jwk,
)

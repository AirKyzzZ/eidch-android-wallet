package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

fun interface IsVeranaTrusted {
    suspend operator fun invoke(did: String): Boolean
}

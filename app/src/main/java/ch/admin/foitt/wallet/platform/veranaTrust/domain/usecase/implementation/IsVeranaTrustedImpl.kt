package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.IsVeranaTrusted
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOr
import javax.inject.Inject

class IsVeranaTrustedImpl @Inject constructor(
    private val resolveVeranaTrust: ResolveVeranaTrust,
) : IsVeranaTrusted {
    override suspend fun invoke(did: String): Boolean = runSuspendCatching {
        resolveVeranaTrust(did).status == VeranaTrustStatus.TRUSTED
    }.getOr(false)
}

package ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.implementation

import android.security.keystore.KeyInfo
import ch.admin.foitt.openid4vc.di.DefaultDispatcher
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.KeySupportsPurposes
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.KeyFactory
import java.security.KeyStore
import javax.inject.Inject

internal class KeySupportsPurposesImpl @Inject constructor(
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : KeySupportsPurposes {
    override suspend operator fun invoke(
        keyAlias: String,
        provider: String,
        purposes: Int,
    ): Boolean = withContext(defaultDispatcher) {
        runSuspendCatching {
            val keyStore = KeyStore.getInstance(provider)
            keyStore.load(null)

            val privateKey = (keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry)?.privateKey
                ?: return@runSuspendCatching false

            val keyInfo = KeyFactory.getInstance(privateKey.algorithm, provider)
                .getKeySpec(privateKey, KeyInfo::class.java)

            keyInfo.purposes and purposes == purposes
        }.onErr { throwable ->
            Timber.w(t = throwable, message = "Could not read purposes of key $keyAlias")
        }.getOr(false)
    }
}

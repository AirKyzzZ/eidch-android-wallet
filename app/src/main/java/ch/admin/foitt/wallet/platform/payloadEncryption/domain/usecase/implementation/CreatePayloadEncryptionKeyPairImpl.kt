package ch.admin.foitt.wallet.platform.payloadEncryption.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialResponseEncryption
import ch.admin.foitt.openid4vc.domain.model.payloadEncryption.EncryptionAlgorithm
import ch.admin.foitt.openid4vc.domain.model.payloadEncryption.PayloadEncryptionKeyPair
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.CreateJWSKeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInSoftware
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.model.CreatePayloadEncryptionKeyPairError
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.model.PayloadEncryptionError
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.model.toCreatePayloadEncryptionKeyPairError
import ch.admin.foitt.wallet.platform.payloadEncryption.domain.usecase.CreatePayloadEncryptionKeyPair
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import javax.inject.Inject

class CreatePayloadEncryptionKeyPairImpl @Inject constructor(
    private val createJWSKeyPairInSoftware: CreateJWSKeyPairInSoftware,
) : CreatePayloadEncryptionKeyPair {
    override suspend fun invoke(
        credentialResponseEncryption: CredentialResponseEncryption,
    ): Result<PayloadEncryptionKeyPair, CreatePayloadEncryptionKeyPairError> = coroutineBinding {
        val keyPair = createJWSKeyPairInSoftware(SigningAlgorithm.ES256)
            .mapError(CreateJWSKeyPairError::toCreatePayloadEncryptionKeyPairError)
            .bind()

        val enc = EncryptionAlgorithm.entries.firstOrNull { algorithm ->
            algorithm.name in credentialResponseEncryption.encValuesSupported
        } ?: return@coroutineBinding Err(
            PayloadEncryptionError.Unexpected(
                IllegalStateException("No supported enc value in ${credentialResponseEncryption.encValuesSupported}")
            )
        ).bind<PayloadEncryptionKeyPair>()

        PayloadEncryptionKeyPair(
            keyPair = keyPair,
            alg = credentialResponseEncryption.algValuesSupported.first(),
            enc = enc.name,
            zip = credentialResponseEncryption.zipValuesSupported?.firstOrNull(),
        )
    }
}

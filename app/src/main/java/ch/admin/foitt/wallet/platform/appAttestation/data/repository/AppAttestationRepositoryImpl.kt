package ch.admin.foitt.wallet.platform.appAttestation.data.repository

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AppAttestationRepositoryError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationChallengeResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationRequest
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.Confirmation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationBatchRequest
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationBatchResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationJwt
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationRequest
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationRequestItem
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.toAppAttestationRepositoryError
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.AppAttestationRepository
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Named

class AppAttestationRepositoryImpl @Inject constructor(
    @param:Named(NAMED_DEFAULT_HTTP_CLIENT) private val httpClient: HttpClient,
    private val environmentSetupRepo: EnvironmentSetupRepository,
) : AppAttestationRepository {
    override suspend fun fetchChallenge(url: String) = runSuspendCatching<AttestationChallengeResponse> {
        httpClient.get("$url/api/attestations/challenge") {
            contentType(ContentType.Application.Json)
        }.body()
    }.mapError { throwable ->
        throwable.toAppAttestationRepositoryError("Fetch attestation challenge failed")
    }

    override suspend fun fetchClientAttestation(
        publicKey: Jwk,
    ): Result<ClientAttestationResponse, AppAttestationRepositoryError> = runSuspendCatching<ClientAttestationResponse> {
        val body = ClientAttestationRequest(
            cnf = Confirmation(publicKey),
        )
        httpClient.post(environmentSetupRepo.defaultAttestationServiceUrl + "/api/attestations/android/client-attestations") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }.mapError { throwable ->
        throwable.toAppAttestationRepositoryError("Fetch client attestation failed")
    }

    override suspend fun fetchKeyAttestation(
        url: String,
        publicKey: Jwk,
    ) = runSuspendCatching<KeyAttestationResponse> {
        val body = KeyAttestationRequest(
            cnf = Confirmation(publicKey),
        )

        httpClient.post("$url/api/attestations/android/key-attestations") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }.mapError { throwable ->
        throwable.toAppAttestationRepositoryError("Fetch key attestation failed")
    }

    override suspend fun fetchBatchKeyAttestation(
        url: String,
        publicKeys: List<Jwk>,
    ): Result<List<KeyAttestationJwt>, AppAttestationRepositoryError> = runSuspendCatching<KeyAttestationBatchResponse> {
        val body = KeyAttestationBatchRequest(
            keys = publicKeys.mapIndexed { index, publicKey ->
                KeyAttestationRequestItem(
                    id = index + FIRST_KEY_ID,
                    cnf = Confirmation(publicKey),
                )
            },
        )

        httpClient.post("$url/api/attestations/android/key-attestations/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }.mapError { throwable ->
        throwable.toAppAttestationRepositoryError("Fetch batch key attestation failed")
    }.andThen { response ->
        response.toAttestationsInRequestOrder(amount = publicKeys.size)
    }

    /**
     * Resolves the response items by their id, never by their position in the response, and returns the attestations
     * in the order the keys were requested in.
     * Anything but exactly one attestation per requested id fails the whole batch.
     */
    private fun KeyAttestationBatchResponse.toAttestationsInRequestOrder(
        amount: Int,
    ): Result<List<KeyAttestationJwt>, AppAttestationRepositoryError> {
        val requestedIds = FIRST_KEY_ID until FIRST_KEY_ID + amount
        val attestationsById = keyAttestations.associate { item -> item.id to item.keyAttestation }

        return when {
            keyAttestations.size != amount -> Err(
                AttestationError.ValidationError("Batch key attestation returned ${keyAttestations.size} of $amount keys")
            )
            attestationsById.keys != requestedIds.toSet() -> Err(
                AttestationError.ValidationError("Batch key attestation ids do not match the requested ids")
            )
            else -> Ok(requestedIds.map(attestationsById::getValue))
        }
    }

    private companion object {
        const val FIRST_KEY_ID = 1
    }
}

package ch.admin.foitt.wallet.platform.appAttestation.di

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.CurrentClientAttestationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode
import timber.log.Timber
import javax.inject.Named

@Module
@InstallIn(ActivityRetainedComponent::class)
object ClientAttestationHttpClientModule {
    @Provides
    @ActivityRetainedScoped
    @Named(NAMED_CLIENT_ATTESTATION_HTTP_CLIENT)
    internal fun provideClientAttestationHttpClient(
        @Named(NAMED_DEFAULT_HTTP_CLIENT) defaultHttpClient: HttpClient,
        currentClientAttestationRepository: CurrentClientAttestationRepository,
    ): HttpClient = defaultHttpClient.config {
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                deleteCachedClientAttestationOnUnauthorized(cause, currentClientAttestationRepository)
            }
        }
    }

    internal suspend fun deleteCachedClientAttestationOnUnauthorized(
        cause: Throwable,
        currentClientAttestationRepository: CurrentClientAttestationRepository,
    ) {
        if (cause is ClientRequestException && cause.response.status == HttpStatusCode.Unauthorized) {
            Timber.w("401 received on request with attestation, removing cached client attestation")
            currentClientAttestationRepository.delete()
        }
    }

    const val NAMED_CLIENT_ATTESTATION_HTTP_CLIENT = "clientAttestationHttpClient"
}

package ch.admin.foitt.wallet.platform.veranaTrust.di

import ch.admin.foitt.wallet.platform.veranaTrust.data.VeranaDocumentRepositoryImpl
import ch.admin.foitt.wallet.platform.veranaTrust.data.VeranaIndexerRepositoryImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaDocumentRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaIndexerRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.CheckVeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.IsVeranaTrusted
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.VerifyVeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.CheckVeranaAccreditationImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.EvaluateVeranaTrustImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.IsVeranaTrustedImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.ResolveVeranaTrustImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.VerifyVeranaDidKeyBindingImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
internal interface VeranaTrustModule {
    @Binds
    fun bindEvaluateVeranaTrust(useCase: EvaluateVeranaTrustImpl): EvaluateVeranaTrust

    @Binds
    @ActivityRetainedScoped
    fun bindResolveVeranaTrust(useCase: ResolveVeranaTrustImpl): ResolveVeranaTrust

    @Binds
    fun bindIsVeranaTrusted(useCase: IsVeranaTrustedImpl): IsVeranaTrusted

    @Binds
    fun bindCheckVeranaAccreditation(useCase: CheckVeranaAccreditationImpl): CheckVeranaAccreditation

    @Binds
    fun bindVerifyVeranaDidKeyBinding(useCase: VerifyVeranaDidKeyBindingImpl): VerifyVeranaDidKeyBinding

    @Binds
    @ActivityRetainedScoped
    fun bindVeranaIndexerRepository(
        repository: VeranaIndexerRepositoryImpl,
    ): VeranaIndexerRepository

    @Binds
    @ActivityRetainedScoped
    fun bindVeranaDocumentRepository(
        repository: VeranaDocumentRepositoryImpl,
    ): VeranaDocumentRepository
}

package ch.admin.foitt.wallet.platform.database.domain.model

import androidx.room.Embedded
import androidx.room.Relation

data class CredentialDisplay(
    @Embedded
    val entity: CredentialDisplayEntity,
    @Relation(
        parentColumn = "logoHash",
        entityColumn = "hash",
    )
    val logo: ImageEntity?,
) : LocalizedDisplay {
    override val locale: String get() = entity.locale
}

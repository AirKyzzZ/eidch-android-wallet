package ch.admin.foitt.wallet.platform.database.domain.model

import androidx.room.Embedded
import androidx.room.Relation

data class CredentialIssuerDisplay(
    @Embedded val entity: CredentialIssuerDisplayEntity,
    @Relation(
        parentColumn = "imageHash",
        entityColumn = "hash"
    )
    val image: ImageEntity?
) : LocalizedDisplay {
    override val locale: String get() = entity.locale
    val name: String? get() = entity.name
}

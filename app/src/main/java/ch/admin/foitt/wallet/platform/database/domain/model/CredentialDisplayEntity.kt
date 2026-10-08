package ch.admin.foitt.wallet.platform.database.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.admin.foitt.wallet.platform.theme.domain.model.Theme

@Entity(
    tableName = "CredentialDisplay",
    foreignKeys = [
        ForeignKey(
            entity = Credential::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("credentialId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = arrayOf("hash"),
            childColumns = arrayOf("logoHash"),
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("credentialId"),
        Index("logoHash")
    ]
)
data class CredentialDisplayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val credentialId: Long, // ForeignKey
    override val locale: String,
    val name: String? = null,
    val description: String? = null,
    val logoHash: String? = null,
    val logoAltText: String? = null,
    val backgroundColor: String? = null,
    val theme: String? = Theme.LIGHT.value
) : LocalizedDisplay

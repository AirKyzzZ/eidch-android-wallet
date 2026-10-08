package ch.admin.foitt.wallet.platform.database.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "CredentialIssuerDisplay",
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
            childColumns = arrayOf("imageHash"),
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("credentialId"),
        Index("imageHash")
    ]
)
data class CredentialIssuerDisplayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val credentialId: Long, // ForeignKey
    val name: String? = null,
    val imageHash: String? = null,
    val imageAltText: String? = null,
    override val locale: String
) : LocalizedDisplay

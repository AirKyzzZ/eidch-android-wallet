package ch.admin.foitt.wallet.platform.database.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplayEntity

@Dao
interface CredentialDisplayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(credentialDisplays: Collection<CredentialDisplayEntity>)

    @Transaction
    @Query("SELECT * FROM credentialdisplay WHERE credentialId = :credentialId")
    fun getByCredentialId(credentialId: Long): List<CredentialDisplay>

    @Query("DELETE FROM credentialdisplay WHERE credentialId = :credentialId")
    fun deleteByCredentialId(credentialId: Long): Int

    @Query("UPDATE credentialdisplay SET logoHash = :hash WHERE id = :id")
    fun updateLogoHash(id: Long, hash: String)
}

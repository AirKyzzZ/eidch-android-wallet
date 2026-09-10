package ch.admin.foitt.wallet.platform.database.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialIssuerDisplayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(credentialIssuerDisplays: Collection<CredentialIssuerDisplayEntity>)

    @Transaction
    @Query("SELECT * FROM credentialissuerdisplay WHERE credentialId = :credentialId")
    fun getCredentialIssuerDisplaysById(credentialId: Long): List<CredentialIssuerDisplay>

    @Transaction
    @Query("SELECT * FROM credentialissuerdisplay WHERE credentialId = :credentialId")
    fun getCredentialIssuerDisplaysFlowById(credentialId: Long): Flow<List<CredentialIssuerDisplay>>

    @Query("DELETE FROM credentialissuerdisplay WHERE credentialId = :credentialId")
    fun deleteByCredentialId(credentialId: Long): Int

    @Query("UPDATE credentialissuerdisplay SET imageHash = :hash WHERE id = :id")
    fun updateImageHash(id: Long, hash: String)
}

package ch.admin.foitt.wallet.platform.database.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import ch.admin.foitt.wallet.platform.database.domain.model.ImageEntity

@Dao
interface ImageEntityDao {
    @Insert(onConflict = IGNORE)
    fun insert(imageEntity: ImageEntity): Long

    @Insert(onConflict = IGNORE)
    fun insertAll(imageEntities: Collection<ImageEntity>)

    @Query("SELECT * FROM imageentity WHERE hash = :hash")
    fun getByHash(hash: String): ImageEntity

    @Query(
        "DELETE FROM imageentity WHERE hash NOT IN (" +
            "SELECT imageHash FROM activityactordisplayentity WHERE imageHash IS NOT NULL " +
            "UNION SELECT logoHash FROM credentialdisplay WHERE logoHash IS NOT NULL " +
            "UNION SELECT imageHash FROM credentialissuerdisplay WHERE imageHash IS NOT NULL" +
            ")"
    )
    fun deleteImagesWithoutChildren()
}

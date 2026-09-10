package ch.admin.foitt.wallet.platform.database.migrations

import androidx.room.util.useCursor
import ch.admin.foitt.wallet.platform.database.data.migrations.Migration30to31
import ch.admin.foitt.wallet.platform.database.util.getStringColumn
import ch.admin.foitt.wallet.platform.database.util.getStringOrNullColumn
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.IOException

class Migration30To31 : BaseDBMigrationTest() {

    @Test
    @Throws(IOException::class)
    fun migrate30To31() {
        var db = helper.createDatabase(testDbName, 30)

        val credentialId = 1L
        db.execSQL(
            "INSERT INTO `Credential` (`id`,`format`,`issuerUrl`,`createdAt`) VALUES " +
                "($credentialId,'format','https://example.com',1000)"
        )

        val displayId1 = 1L
        val displayId2 = 2L
        db.execSQL(
            "INSERT INTO `CredentialDisplay` (`id`,`credentialId`,`locale`,`name`,`logoUri`) VALUES " +
                "($displayId1,$credentialId,'en','Name 1','https://example.com/logo1.png')," +
                "($displayId2,$credentialId,'de','Name 2',NULL)"
        )

        val issuerDisplayId1 = 1L
        val issuerDisplayId2 = 2L
        val issuerDisplayId3 = 3L
        db.execSQL(
            "INSERT INTO `CredentialIssuerDisplay` (`id`,`credentialId`,`name`,`image`,`locale`) VALUES " +
                "($issuerDisplayId1,$credentialId,'Issuer 1','https://example.com/image1.png','en')," +
                "($issuerDisplayId2,$credentialId,'Unknown Issuer','https://example.com/image2.png','de')," +
                "($issuerDisplayId3,$credentialId,'Issuer 3',NULL,'fr')"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 31, true, Migration30to31)

        db.query("SELECT `id`, `logoHash` FROM `CredentialDisplay` ORDER BY `id` ASC").useCursor { cursor ->
            cursor.moveToFirst()
            assertEquals(displayId1, cursor.getLong(0))
            assertEquals(null, cursor.getStringOrNullColumn("logoHash"))

            cursor.moveToNext()
            assertEquals(displayId2, cursor.getLong(0))
            assertEquals(null, cursor.getStringOrNullColumn("logoHash"))
        }

        db.query("SELECT `id`, `name`, `imageHash` FROM `CredentialIssuerDisplay` ORDER BY `id` ASC").useCursor { cursor ->
            cursor.moveToFirst()
            assertEquals(issuerDisplayId1, cursor.getLong(0))
            assertEquals("Issuer 1", cursor.getStringColumn("name"))
            assertEquals(null, cursor.getStringOrNullColumn("imageHash"))

            cursor.moveToNext()
            assertEquals(issuerDisplayId2, cursor.getLong(0))
            assertEquals(null, cursor.getStringOrNullColumn("name"))
            assertEquals(null, cursor.getStringOrNullColumn("imageHash"))

            cursor.moveToNext()
            assertEquals(issuerDisplayId3, cursor.getLong(0))
            assertEquals("Issuer 3", cursor.getStringColumn("name"))
            assertEquals(null, cursor.getStringOrNullColumn("imageHash"))
        }
    }
}

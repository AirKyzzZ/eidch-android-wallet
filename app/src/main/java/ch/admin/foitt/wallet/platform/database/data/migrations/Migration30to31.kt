package ch.admin.foitt.wallet.platform.database.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val Migration30to31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Migrate CredentialIssuerDisplay: nullable 'name', 'image' -> 'imageHash'
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `CredentialIssuerDisplay_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `credentialId` INTEGER NOT NULL, 
                `name` TEXT, 
                `imageHash` TEXT, 
                `imageAltText` TEXT, 
                `locale` TEXT NOT NULL, 
                FOREIGN KEY(`credentialId`) REFERENCES `Credential`(`id`) ON UPDATE CASCADE ON DELETE CASCADE, 
                FOREIGN KEY(`imageHash`) REFERENCES `ImageEntity`(`hash`) ON UPDATE CASCADE ON DELETE NO ACTION
            )
        """
        )

        db.execSQL(
            """
            INSERT INTO `CredentialIssuerDisplay_new` (id, credentialId, name, imageHash, imageAltText, locale)
            SELECT id, 
                   credentialId, 
                   CASE WHEN name = 'Unknown Issuer' THEN NULL ELSE name END, 
                   NULL, -- Set to NULL because URIs are not valid Hashes
                   imageAltText, 
                   locale 
            FROM `CredentialIssuerDisplay`
        """
        )

        db.execSQL("DROP TABLE `CredentialIssuerDisplay`")
        db.execSQL("ALTER TABLE `CredentialIssuerDisplay_new` RENAME TO `CredentialIssuerDisplay` ")

        // Migrate CredentialDisplay: 'logoUri' -> 'logoHash'
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `CredentialDisplay_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `credentialId` INTEGER NOT NULL, 
                `locale` TEXT NOT NULL, 
                `name` TEXT, 
                `description` TEXT, 
                `logoHash` TEXT, 
                `logoAltText` TEXT, 
                `backgroundColor` TEXT, 
                `theme` TEXT, 
                FOREIGN KEY(`credentialId`) REFERENCES `Credential`(`id`) ON UPDATE CASCADE ON DELETE CASCADE, 
                FOREIGN KEY(`logoHash`) REFERENCES `ImageEntity`(`hash`) ON UPDATE CASCADE ON DELETE NO ACTION
            )
        """
        )

        db.execSQL(
            """
            INSERT INTO `CredentialDisplay_new` (id, credentialId, locale, name, description, logoHash, logoAltText, backgroundColor, theme)
            SELECT id, credentialId, locale, name, description, NULL, logoAltText, backgroundColor, theme
            FROM `CredentialDisplay`
        """
        )

        db.execSQL("DROP TABLE `CredentialDisplay`")
        db.execSQL("ALTER TABLE `CredentialDisplay_new` RENAME TO `CredentialDisplay` ")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_CredentialIssuerDisplay_credentialId` ON `CredentialIssuerDisplay` (`credentialId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_CredentialIssuerDisplay_imageHash` ON `CredentialIssuerDisplay` (`imageHash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_CredentialDisplay_credentialId` ON `CredentialDisplay` (`credentialId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_CredentialDisplay_logoHash` ON `CredentialDisplay` (`logoHash`)")
    }
}

package ch.admin.foitt.openid4vc.domain.model.vcSdJwt

import ch.admin.foitt.openid4vc.util.SafeJsonTestInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeMetadataTest {
    @Test
    fun `Decoding type metadata reads the display language from lang`() {
        val typeMetadata = SafeJsonTestInstance.json.decodeFromString<TypeMetadata>(TYPE_METADATA_WITH_LANG)

        assertEquals("de-CH", typeMetadata.displays?.single()?.lang)
        assertEquals("Beispiel", typeMetadata.displays?.single()?.name)
    }

    @Test
    fun `Decoding type metadata reads the display language from locale`() {
        val typeMetadata = SafeJsonTestInstance.json.decodeFromString<TypeMetadata>(TYPE_METADATA_WITH_LOCALE)

        assertEquals("en", typeMetadata.displays?.single()?.lang)
        assertEquals("DemoCredential", typeMetadata.displays?.single()?.name)
    }

    private companion object {
        const val TYPE_METADATA_WITH_LANG = """
            {
              "vct": "https://example.com/vct",
              "display": [{ "lang": "de-CH", "name": "Beispiel" }]
            }
        """

        const val TYPE_METADATA_WITH_LOCALE = """
            {
              "vct": "https://playground-demo.playground.devnet.verana.network/vt/vct/7",
              "name": "DemoCredential",
              "description": "The single credential of the Playground Ecosystem (demo).",
              "display": [
                {
                  "locale": "en",
                  "name": "DemoCredential",
                  "description": "The single credential of the Playground Ecosystem (demo)."
                }
              ],
              "claims": [
                { "path": ["demoId"], "display": [{ "locale": "en", "label": "demoId" }], "sd": "always" },
                { "path": ["name"], "display": [{ "locale": "en", "label": "name" }], "sd": "always" }
              ],
              "relatedJsonSchemaCredentialId": "https://playground-demo.playground.devnet.verana.network/vt/schemas-7-jsc.json"
            }
        """
    }
}

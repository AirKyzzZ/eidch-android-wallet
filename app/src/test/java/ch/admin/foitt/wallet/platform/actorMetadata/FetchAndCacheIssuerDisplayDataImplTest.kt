package ch.admin.foitt.wallet.platform.actorMetadata

import android.webkit.URLUtil
import ch.admin.foitt.openid4vc.domain.model.anycredential.AnyCredential
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorMetaDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.ActorUpdateGate
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.CacheIssuerDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.FetchAndCacheIssuerDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation.FetchAndCacheIssuerDisplayDataImpl
import ch.admin.foitt.wallet.platform.credential.domain.model.AnyIssuerDisplay
import ch.admin.foitt.wallet.platform.credential.domain.model.CredentialError
import ch.admin.foitt.wallet.platform.credential.domain.usecase.FetchTrustForIssuance
import ch.admin.foitt.wallet.platform.credential.domain.usecase.GetAllAnyCredentialsByCredentialId
import ch.admin.foitt.wallet.platform.credential.domain.usecase.implementation.mock.MockNonComplianceData
import ch.admin.foitt.wallet.platform.database.domain.model.Credential
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplayEntity
import ch.admin.foitt.wallet.platform.database.domain.model.DisplayLanguage
import ch.admin.foitt.wallet.platform.database.domain.model.ImageEntity
import ch.admin.foitt.wallet.platform.database.domain.model.RawCredentialData
import ch.admin.foitt.wallet.platform.ssi.domain.model.SsiError
import ch.admin.foitt.wallet.platform.ssi.domain.repository.CredentialIssuerDisplayRepo
import ch.admin.foitt.wallet.platform.ssi.domain.repository.CredentialRepo
import ch.admin.foitt.wallet.platform.ssi.domain.repository.RawCredentialDataRepository
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.IdentityV2TrustStatement
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustCheckResult
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustRegistryError
import ch.admin.foitt.wallet.platform.trustRegistry.domain.usecase.ProcessIdentityTrustStatement
import ch.admin.foitt.wallet.platform.utils.compress
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import ch.admin.foitt.wallet.util.SafeJsonTestInstance
import ch.admin.foitt.wallet.util.assertErrorType
import ch.admin.foitt.wallet.util.assertOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FetchAndCacheIssuerDisplayDataImplTest {

    @MockK
    private lateinit var mockGetAllAnyCredentialByCredentialId: GetAllAnyCredentialsByCredentialId

    @MockK
    private lateinit var mockRawCredentialDataRepository: RawCredentialDataRepository

    @MockK
    private lateinit var mockCredentialRepo: CredentialRepo

    @MockK
    private lateinit var mockProcessIdentityTrustStatement: ProcessIdentityTrustStatement

    @MockK
    private lateinit var mockFetchTrustForIssuance: FetchTrustForIssuance

    @MockK
    private lateinit var mockEvaluateVeranaTrust: EvaluateVeranaTrust

    @MockK
    private lateinit var mockCredentialIssuerDisplayRepo: CredentialIssuerDisplayRepo

    @MockK
    private lateinit var mockCacheIssuerDisplayData: CacheIssuerDisplayData

    private val nonComplianceData = MockNonComplianceData.nonComplianceData

    @MockK
    private lateinit var mockTrustedTrustCheckResult: TrustCheckResult

    @MockK
    private lateinit var mockIdentityTrustStatement: IdentityV2TrustStatement

    @MockK
    private lateinit var mockAnyCredential: AnyCredential

    @MockK
    private lateinit var mockRawCredentialData: RawCredentialData

    @MockK
    private lateinit var mockCredential: Credential

    private val safeJson = SafeJsonTestInstance.safeJson

    private lateinit var useCase: FetchAndCacheIssuerDisplayData

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = FetchAndCacheIssuerDisplayDataImpl(
            getAllAnyCredentialsByCredentialId = mockGetAllAnyCredentialByCredentialId,
            rawCredentialDataRepository = mockRawCredentialDataRepository,
            safeJson = safeJson,
            credentialRepository = mockCredentialRepo,
            processIdentityTrustStatement = mockProcessIdentityTrustStatement,
            fetchTrustForIssuance = mockFetchTrustForIssuance,
            evaluateVeranaTrust = mockEvaluateVeranaTrust,
            credentialIssuerDisplayRepo = mockCredentialIssuerDisplayRepo,
            cacheIssuerDisplayData = mockCacheIssuerDisplayData,
            actorUpdateGate = ActorUpdateGate(),
        )

        setupDefaultMocks()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `A successful execution follow specific steps`() = runTest {
        useCase(credentialId = CREDENTIAL_ID).assertOk()

        coVerifyOrder {
            mockGetAllAnyCredentialByCredentialId(credentialId = CREDENTIAL_ID)
            mockRawCredentialDataRepository.getByCredentialId(CREDENTIAL_ID)
            mockProcessIdentityTrustStatement(
                identityTrustStatementJwt = any(),
                actorDid = ISSUER_DID,
            )
            mockCredentialRepo.getById(CREDENTIAL_ID)
            mockFetchTrustForIssuance(
                identityTrustStatement = mockIdentityTrustStatement,
                protectedIssuanceAuthorizationTrustStatement = null,
                issuerDid = ISSUER_DID,
                vcSchemaId = VC_SCHEMA_ID,
            )
            mockEvaluateVeranaTrust(
                role = VeranaTrustRole.ISSUER,
                did = ISSUER_DID,
                vct = null,
                certificateKey = null,
            )
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(credentialId = CREDENTIAL_ID)
            mockCacheIssuerDisplayData(
                trustCheckResult = mockTrustedTrustCheckResult,
                issuerDisplays = any(),
                nonComplianceData = nonComplianceData,
                veranaTrustEvidence = veranaTrustEvidence,
            )
        }
    }

    @Test
    fun `The cached display uses entity name from the db`() = runTest {
        coEvery {
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(credentialId = any())
        } returns Ok(listOf(issuerDisplayData1, issuerDisplayData2))

        useCase(credentialId = CREDENTIAL_ID).assertOk()

        val expectedIssuerDisplays = listOf(
            AnyIssuerDisplay(
                locale = issuerDisplayData1.locale,
                name = issuerDisplayData1.name,
                logoImageData = issuerDisplayData1.image?.image,
                logoAltText = issuerDisplayData1.entity.imageAltText,
            ),
            AnyIssuerDisplay(
                locale = issuerDisplayData2.locale,
                name = issuerDisplayData2.name,
                logoImageData = issuerDisplayData2.image?.image,
                logoAltText = issuerDisplayData2.entity.imageAltText,
            ),
        )

        coVerify {
            mockCacheIssuerDisplayData(
                trustCheckResult = any(),
                issuerDisplays = expectedIssuerDisplays,
                nonComplianceData = any(),
                veranaTrustEvidence = veranaTrustEvidence,
            )
        }
    }

    @Test
    fun `A display without a cached image falls back to the metadata logo uri`() = runTest {
        every { mockRawCredentialData.rawOIDMetadata } returns compressedIssuerCredentialInfoWithDisplay
        coEvery {
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(credentialId = any())
        } returns Ok(listOf(issuerDisplayData1, issuerDisplayData2))

        useCase(credentialId = CREDENTIAL_ID).assertOk()

        val expectedIssuerDisplays = listOf(
            AnyIssuerDisplay(
                locale = DISPLAY_LOCALE1,
                name = "issuerName1",
                logo = LOGO_URI1,
                logoImageData = null,
                logoAltText = "issuerImageAltText1",
            ),
            AnyIssuerDisplay(
                locale = DISPLAY_LOCALE2,
                name = "issuerName2",
                logo = null,
                logoImageData = null,
                logoAltText = "issuerImageAltText2",
            ),
        )

        coVerify {
            mockCacheIssuerDisplayData(
                trustCheckResult = any(),
                issuerDisplays = expectedIssuerDisplays,
                nonComplianceData = any(),
                veranaTrustEvidence = veranaTrustEvidence,
            )
        }
    }

    @Test
    fun `A display with a cached image and the fallback display do not get a metadata logo`() = runTest {
        every { mockRawCredentialData.rawOIDMetadata } returns compressedIssuerCredentialInfoWithDisplay
        coEvery {
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(credentialId = any())
        } returns Ok(listOf(issuerDisplayData1WithImage, fallbackIssuerDisplay))

        useCase(credentialId = CREDENTIAL_ID).assertOk()

        val expectedIssuerDisplays = listOf(
            AnyIssuerDisplay(
                locale = DISPLAY_LOCALE1,
                name = "issuerName1",
                logo = null,
                logoImageData = LOGO_IMAGE_DATA1,
                logoAltText = "issuerImageAltText1",
            ),
            AnyIssuerDisplay(
                locale = DisplayLanguage.FALLBACK,
                name = "issuerNameFallback",
                logo = null,
                logoImageData = null,
                logoAltText = "issuerImageAltTextFallback",
            ),
        )

        coVerify {
            mockCacheIssuerDisplayData(
                trustCheckResult = any(),
                issuerDisplays = expectedIssuerDisplays,
                nonComplianceData = any(),
                veranaTrustEvidence = veranaTrustEvidence,
            )
        }
    }

    @Test
    fun `A GetAnyCredential error is mapped`() = runTest {
        val exception = Exception("my exception")
        coEvery { mockGetAllAnyCredentialByCredentialId(any()) } returns Err(CredentialError.Unexpected(exception))

        val error = useCase(
            credentialId = CREDENTIAL_ID,
        ).assertErrorType(ActorMetaDataError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    @Test
    fun `A RawCredentialData repo error is mapped`() = runTest {
        val exception = Exception("my exception")
        coEvery { mockRawCredentialDataRepository.getByCredentialId(any()) } returns Err(SsiError.Unexpected(exception))

        val error = useCase(
            credentialId = CREDENTIAL_ID,
        ).assertErrorType(ActorMetaDataError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    @Test
    fun `Missing raw metadata error is mapped`() = runTest {
        every { mockRawCredentialData.rawOIDMetadata } returns null

        useCase(CREDENTIAL_ID).assertErrorType(ActorMetaDataError.Unexpected::class)
    }

    @Test
    fun `A Credential repo error is mapped`() = runTest {
        val exception = Exception("my exception")
        coEvery { mockCredentialRepo.getById(any()) } returns Err(SsiError.Unexpected(exception))

        val error = useCase(
            credentialId = CREDENTIAL_ID,
        ).assertErrorType(ActorMetaDataError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    @Test
    fun `Not found credential configuration identifier returns null config`() = runTest {
        every { mockCredential.selectedConfigurationId } returns "otherIdentifier"

        useCase(
            credentialId = CREDENTIAL_ID,
        ).assertOk()

        coVerify {
            mockFetchTrustForIssuance(any(), null, any(), any())
        }
    }

    @Test
    fun `An empty trustCheckResult does not stop the execution`() = runTest {
        coEvery { mockTrustedTrustCheckResult.identityTrustStatement } returns null

        useCase(CREDENTIAL_ID).assertOk()
    }

    @Test
    fun `A processIdentityTrustStatement error is mapped to unverified issuer`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockProcessIdentityTrustStatement(any(), any())
        } returns Err(TrustRegistryError.Unexpected(exception))

        useCase(CREDENTIAL_ID).assertErrorType(ActorMetaDataError.UnverifiedIssuer::class)
    }

    @Test
    fun `A fetchTrustForIssuance error is mapped`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockFetchTrustForIssuance(any(), any(), any(), any())
        } returns Err(CredentialError.Unexpected(exception))

        val error = useCase(CREDENTIAL_ID).assertErrorType(ActorMetaDataError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    @Test
    fun `A credentialIssuer repository error is mapped`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(any())
        } returns Err(SsiError.Unexpected(exception))

        val error = useCase(CREDENTIAL_ID).assertErrorType(ActorMetaDataError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    @Test
    fun `A caching exception is not caught`() = runTest {
        coEvery {
            mockCacheIssuerDisplayData(any(), any(), any(), any())
        } throws IllegalStateException("my exception")

        assertThrows<IllegalStateException> {
            useCase(CREDENTIAL_ID)
        }
    }

    @Test
    fun `Verana evaluates the issuer of a single-issuer bundle once`() = runTest {
        val secondCredential = mockk<AnyCredential>()
        every { secondCredential.issuer } returns ISSUER_DID
        every { secondCredential.vcSchemaId } returns VC_SCHEMA_ID_2
        coEvery {
            mockGetAllAnyCredentialByCredentialId(CREDENTIAL_ID)
        } returns Ok(listOf(mockAnyCredential, secondCredential, mockAnyCredential))

        useCase(CREDENTIAL_ID).assertOk()

        coVerify(exactly = 1) {
            mockEvaluateVeranaTrust(
                role = VeranaTrustRole.ISSUER,
                did = ISSUER_DID,
                vct = null,
                certificateKey = null,
            )
        }
    }

    @Test
    fun `mixed verified credential issuers are unverified without asking Verana`() = runTest {
        val secondCredential = mockk<AnyCredential>()
        every { secondCredential.issuer } returns OTHER_ISSUER_DID
        every { secondCredential.vcSchemaId } returns VC_SCHEMA_ID_2
        coEvery {
            mockGetAllAnyCredentialByCredentialId(CREDENTIAL_ID)
        } returns Ok(listOf(mockAnyCredential, secondCredential))

        useCase(CREDENTIAL_ID).assertOk()

        coVerify(exactly = 0) { mockEvaluateVeranaTrust(any(), any(), any(), any()) }
        coVerify {
            mockCacheIssuerDisplayData(
                trustCheckResult = any(),
                issuerDisplays = any(),
                nonComplianceData = any(),
                veranaTrustEvidence = match { it?.resolution?.status == VeranaTrustStatus.UNVERIFIED },
            )
        }
    }

    @Test
    fun `an issuer without a DID keeps the normal flow`() = runTest {
        every { mockAnyCredential.issuer } returns "https://issuer.example"
        coEvery { mockProcessIdentityTrustStatement(any(), any()) } returns Ok(mockIdentityTrustStatement)

        useCase(CREDENTIAL_ID).assertOk()

        coVerify(exactly = 0) { mockEvaluateVeranaTrust(any(), any(), any(), any()) }
        coVerify {
            mockCacheIssuerDisplayData(
                trustCheckResult = any(),
                issuerDisplays = any(),
                nonComplianceData = any(),
                veranaTrustEvidence = null,
            )
        }
    }

    private fun setupDefaultMocks() {
        coEvery { mockGetAllAnyCredentialByCredentialId(credentialId = any()) } returns Ok(listOf(mockAnyCredential))
        coEvery { mockRawCredentialDataRepository.getByCredentialId(any()) } returns Ok(mockRawCredentialData)
        coEvery { mockCredentialRepo.getById(any()) } returns Ok(mockCredential)
        coEvery {
            mockProcessIdentityTrustStatement(any(), ISSUER_DID)
        } returns Ok(mockIdentityTrustStatement)
        coEvery {
            mockFetchTrustForIssuance(any(), any(), any(), any())
        } returns Ok(mockTrustedTrustCheckResult)
        coEvery {
            mockCredentialIssuerDisplayRepo.getIssuerDisplays(credentialId = any())
        } returns Ok(listOf(issuerDisplayData1))
        coEvery {
            mockCacheIssuerDisplayData(any(), any(), any(), any())
        } just Runs
        coEvery {
            mockEvaluateVeranaTrust(any(), any(), any(), any())
        } returns veranaTrustEvidence

        every { mockAnyCredential.id } returns CREDENTIAL_ID
        every { mockAnyCredential.issuer } returns ISSUER_DID
        every { mockAnyCredential.vcSchemaId } returns VC_SCHEMA_ID

        every { mockRawCredentialData.rawOIDMetadata } returns compressedIssuerCredentialInfo
        mockkStatic(URLUtil::class)
        every { URLUtil.isHttpsUrl(any()) } returns true

        every { mockCredential.selectedConfigurationId } returns "identifier"

        every { mockTrustedTrustCheckResult.identityTrustStatement } returns mockIdentityTrustStatement
        every { mockTrustedTrustCheckResult.nonComplianceData } returns nonComplianceData
        every { mockIdentityTrustStatement.entityName } returns TRUST_ISSUER_NAMES
    }

    private companion object {
        const val CREDENTIAL_ID = 1L
        const val ISSUER_DID = "did:web:issuer.example"
        const val OTHER_ISSUER_DID = "did:web:other.example"
        const val VC_SCHEMA_ID = "vcSchemaId"
        const val VC_SCHEMA_ID_2 = "vcSchemaId2"
        const val DISPLAY_LOCALE1 = "displayLocale1"
        const val DISPLAY_LOCALE2 = "displayLocale2"
        const val TRUST_ISSUER_NAME1 = "trustIssuerName1"
        const val TRUST_ISSUER_NAME2 = "trustIssuerName2"
        const val LOGO_URI1 = "https://example.com/logo1.png"
        val LOGO_IMAGE_DATA1 = byteArrayOf(1, 2, 3)

        val TRUST_ISSUER_NAMES = mapOf(
            DISPLAY_LOCALE1 to TRUST_ISSUER_NAME1,
            DISPLAY_LOCALE2 to TRUST_ISSUER_NAME2,
        )

        val issuerDisplayData1 = CredentialIssuerDisplay(
            entity = CredentialIssuerDisplayEntity(
                id = 1L,
                credentialId = CREDENTIAL_ID,
                name = "issuerName1",
                imageAltText = "issuerImageAltText1",
                locale = DISPLAY_LOCALE1,
            ),
            image = null,
        )

        val issuerDisplayData2 = CredentialIssuerDisplay(
            entity = CredentialIssuerDisplayEntity(
                id = 2L,
                credentialId = CREDENTIAL_ID,
                name = "issuerName2",
                imageAltText = "issuerImageAltText2",
                locale = DISPLAY_LOCALE2,
            ),
            image = null

        )

        const val IDENTITY_TRUST_STATEMENT_JWT_STRING =
            "eyJ0eXAiOiJzd2l5dS1pZGVudGl0eS10cnVzdC1zdGF0ZW1lbnQrand0IiwiYWxnIjoiRVMyNTYiLCJraWQiOiJkaWQ6ZXhhbXBsZTp0cnVzdC1pc3N1ZXIja2V5LTAxIiwicHJvZmlsZV92ZXJzaW9uIjoic3dpc3MtcHJvZmlsZS10cnVzdDoxLjAuMCJ9.eyJqdGkiOiJqdGkiLCJpYXQiOjAsImV4cCI6MTg3NTA3MDAwOSwic3ViIjoiZGlkOmV4YW1wbGU6YWN0b3IiLCJzdGF0dXMiOnsic3RhdHVzX2xpc3QiOnsiaWR4IjowLCJ1cmkiOiJodHRwczovL2V4YW1wbGUuY29tL3N0YXR1c2xpc3RzLzEifX0sImVudGl0eV9uYW1lIjoiSm9obiBTbWl0aCdzIFNtaXRoZXJ5IiwiZW50aXR5X25hbWUjZGUiOiJKb2huIFNtaXRoJ3MgU2NobWlkZXJlaSIsImVudGl0eV9uYW1lI2RlLUNIIjoiSm9obiBTbWl0aCdzIFNjaG1pZGVyZWkiLCJpc19zdGF0ZV9hY3RvciI6ZmFsc2UsInJlZ2lzdHJ5X2lkcyI6W3sidHlwZSI6IlVJRCIsInZhbHVlIjoiQ0hFLTAwMC4wMDAuMDAwIn0seyJ0eXBlIjoiTEVJIiwidmFsdWUiOiIwQTFCMkMzRDRFNUY2RzdIOEo5SSJ9XX0.hhfeCNqx9jaYS6ZQAmOIxO0dWDutxGvnyXsMzWkSOJf1FAfoDOyB4cVjZ0lPXPbfrdYHzLpEM_zlGPVSy3MbWg"
        val issuerCredentialInfo = """
            {
                "credential_issuer": "https://example.com",
                "credential_endpoint": "https://example.com/credential",
                "nonce_endpoint": "https://example.com/nonce",
                "credential_issuer_identity_trust_statement": "$IDENTITY_TRUST_STATEMENT_JWT_STRING",
                "credential_request_encryption":{
                    "enc_values_supported":[
                        "A128GCM",
                        "A256GCM"
                    ],
                    "zip_values_supported":[
                        "DEF"
                    ],
                    "encryption_required":false,
                    "jwks":{
                        "keys":[
                            {
                                "kty":"EC",
                                "crv":"P-256",
                                "kid":"7ff5adfd-811c-4ccc-ab8c-aaf0bbfd33d2",
                                "x":"Q-21f1nn5YsTSGvh0wrZFilUcDMNH1NHCsrDNrnep5I",
                                "y":"FSU_XURrqId0PVWckqTHeFS9ivSdpE5Zgn71uj5cb2w",
                                "alg":"ECDH-ES"
                            }
                        ]
                    }
                },
                "credential_response_encryption":{
                    "enc_values_supported":[
                        "A128GCM",
                        "A256GCM"
                    ],
                    "zip_values_supported":[
                        "DEF"
                    ],
                    "encryption_required":false,
                    "alg_values_supported":[
                        "ECDH-ES"
                    ]
                },
                "credential_configurations_supported": {
                    "identifier": {
                        "format": "vc+sd-jwt",
                        "vct": "identifier",
                        "credential_signing_alg_values_supported": [
                            "ES256"
                        ]
                    }
                }
            }
        """.trimIndent()
        val compressedIssuerCredentialInfo = issuerCredentialInfo.toByteArray().compress()

        val issuerDisplayData1WithImage = CredentialIssuerDisplay(
            entity = CredentialIssuerDisplayEntity(
                id = 1L,
                credentialId = CREDENTIAL_ID,
                name = "issuerName1",
                imageAltText = "issuerImageAltText1",
                locale = DISPLAY_LOCALE1,
            ),
            image = ImageEntity(hash = "logoHash1", image = LOGO_IMAGE_DATA1),
        )

        val fallbackIssuerDisplay = CredentialIssuerDisplay(
            entity = CredentialIssuerDisplayEntity(
                id = 3L,
                credentialId = CREDENTIAL_ID,
                name = "issuerNameFallback",
                imageAltText = "issuerImageAltTextFallback",
                locale = DisplayLanguage.FALLBACK,
            ),
            image = null,
        )

        val displayJson =
            "\"display\": [" +
                "{\"locale\": \"$DISPLAY_LOCALE1\", \"name\": \"issuerName1\", \"logo\": {\"uri\": \"$LOGO_URI1\", \"alt_text\": \"issuerImageAltText1\"}}," +
                "{\"locale\": \"$DISPLAY_LOCALE2\", \"name\": \"issuerName2\"}" +
                "], "

        val issuerCredentialInfoWithDisplay = issuerCredentialInfo.replace(
            "\"credential_endpoint\":",
            "$displayJson\"credential_endpoint\":",
        )
        val compressedIssuerCredentialInfoWithDisplay = issuerCredentialInfoWithDisplay.toByteArray().compress()

        val veranaTrustEvidence = VeranaTrustEvidence(
            role = VeranaTrustRole.ISSUER,
            did = ISSUER_DID,
            vct = null,
            resolution = VeranaTrustResolution(did = ISSUER_DID, status = VeranaTrustStatus.TRUSTED),
            accreditation = null,
        )
    }
}

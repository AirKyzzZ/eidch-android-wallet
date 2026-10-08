package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorField
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorImageSource
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.GetLocalizedActorImageData
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.utils.domain.usecase.GetImageDataFromUri
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetLocalizedActorImageDataImplTest {

    @MockK
    private lateinit var mockGetLocalizedDisplay: GetLocalizedDisplay

    @MockK
    private lateinit var mockGetImageDataFromUri: GetImageDataFromUri

    private lateinit var useCase: GetLocalizedActorImageData

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = GetLocalizedActorImageDataImpl(
            getLocalizedDisplay = mockGetLocalizedDisplay,
            getImageDataFromUri = mockGetImageDataFromUri,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Getting actor image data from imageData returns the bytes`() = runTest {
        val imageData = "imageData".toByteArray()
        val actorDisplayData = ActorDisplayData.EMPTY.copy(
            imageData = listOf(ActorField(value = imageData, locale = "de")),
            preferredLanguage = "de"
        )

        every {
            mockGetLocalizedDisplay(any<List<ActorImageSource>>(), "de")
        } returns ActorImageSource("de", imageData, null)

        val result = useCase(actorDisplayData)

        assertArrayEquals(imageData, result)
    }

    @Test
    fun `Getting actor image data from image URI returns the bytes`() = runTest {
        val iconUri = "iconUri"
        val iconBytes = "iconBytes".toByteArray()
        val actorDisplayData = ActorDisplayData.EMPTY.copy(
            image = listOf(ActorField(value = iconUri, locale = "de")),
            preferredLanguage = "de"
        )

        every {
            mockGetLocalizedDisplay(any<List<ActorImageSource>>(), "de")
        } returns ActorImageSource("de", null, iconUri)

        coEvery { mockGetImageDataFromUri(iconUri) } returns iconBytes

        val result = useCase(actorDisplayData)

        assertArrayEquals(iconBytes, result)
    }

    @Test
    fun `Getting actor image data prioritizes imageData over image URI`() = runTest {
        val imageData = "imageData".toByteArray()
        val iconUri = "iconUri"
        val actorDisplayData = ActorDisplayData.EMPTY.copy(
            imageData = listOf(ActorField(value = imageData, locale = "de")),
            image = listOf(ActorField(value = iconUri, locale = "de")),
            preferredLanguage = "de"
        )

        every {
            mockGetLocalizedDisplay(any<List<ActorImageSource>>(), "de")
        } returns ActorImageSource("de", imageData, iconUri)

        val result = useCase(actorDisplayData)

        assertArrayEquals(imageData, result)
        coVerify(exactly = 0) { mockGetImageDataFromUri(any()) }
    }

    @Test
    fun `Getting actor image data returns null if no image is found`() = runTest {
        val actorDisplayData = ActorDisplayData.EMPTY

        val result = useCase(actorDisplayData)

        assertNull(result)
    }

    @Test
    fun `Getting actor image data resolves the preferred locale across mixed sources`() = runTest {
        // The de blob is backfilled and the fr URI is not: the preferred language still wins
        val deData = "deImageData".toByteArray()
        val frUri = "frIconUri"
        val frBytes = "frIconBytes".toByteArray()
        val actorDisplayData = ActorDisplayData.EMPTY.copy(
            imageData = listOf(ActorField(value = deData, locale = "de")),
            image = listOf(ActorField(value = frUri, locale = "fr")),
            preferredLanguage = "fr"
        )

        every {
            mockGetLocalizedDisplay(any<List<ActorImageSource>>(), "fr")
        } returns ActorImageSource("fr", null, frUri)

        coEvery { mockGetImageDataFromUri(frUri) } returns frBytes

        val result = useCase(actorDisplayData)

        assertArrayEquals(frBytes, result)
        coVerify(exactly = 1) { mockGetImageDataFromUri(frUri) }
    }
}

package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaIndexerAnswer
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaNetwork
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaUntrustedReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaIndexerRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ResolveVeranaTrustImplTest {

    @MockK
    private lateinit var indexer: VeranaIndexerRepository

    private lateinit var useCase: ResolveVeranaTrustImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = ResolveVeranaTrustImpl(indexer, listOf(TESTNET, DEVNET))
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a DID without a DID document is untrusted without asking any network`() = runTest {
        val resolution = useCase("did:key:z6MkExample")

        assertEquals(VeranaTrustStatus.UNTRUSTED, resolution.status)
        assertEquals(VeranaUntrustedReason.NO_DID_DOCUMENT, resolution.reason)
        coVerify(exactly = 0) { indexer.resolve(any(), any()) }
    }

    @Test
    fun `settles TRUSTED as soon as one network trusts the DID`() = runTest {
        coEvery { indexer.resolve(TESTNET, DID) } coAnswers { awaitCancellation() }
        coEvery { indexer.resolve(DEVNET, DID) } returns resolved(trusted = true, network = DEVNET)

        val resolution = useCase(DID)

        assertEquals(VeranaTrustStatus.TRUSTED, resolution.status)
        assertEquals(DEVNET, resolution.network)
    }

    @Test
    fun `is UNTRUSTED when every network answers and none trusts`() = runTest {
        coEvery { indexer.resolve(TESTNET, DID) } returns VeranaIndexerAnswer.NotRegistered
        coEvery { indexer.resolve(DEVNET, DID) } returns resolved(trusted = false, network = DEVNET)

        val resolution = useCase(DID)

        assertEquals(VeranaTrustStatus.UNTRUSTED, resolution.status)
        assertEquals(VeranaUntrustedReason.NOT_TRUSTED, resolution.reason)
        assertEquals(DEVNET, resolution.network)
    }

    @Test
    fun `is UNTRUSTED for a DID no network knows`() = runTest {
        coEvery { indexer.resolve(any(), DID) } returns VeranaIndexerAnswer.NotRegistered

        val resolution = useCase(DID)

        assertEquals(VeranaTrustStatus.UNTRUSTED, resolution.status)
        assertEquals(VeranaUntrustedReason.NOT_REGISTERED, resolution.reason)
    }

    @Test
    fun `is UNVERIFIED when a network did not answer and none trusts`() = runTest {
        coEvery { indexer.resolve(TESTNET, DID) } returns VeranaIndexerAnswer.Unanswered
        coEvery { indexer.resolve(DEVNET, DID) } returns VeranaIndexerAnswer.NotRegistered

        assertEquals(VeranaTrustStatus.UNVERIFIED, useCase(DID).status)
    }

    @Test
    fun `is UNVERIFIED without any network`() = runTest {
        assertEquals(VeranaTrustStatus.UNVERIFIED, ResolveVeranaTrustImpl(indexer, emptyList())(DID).status)
    }

    @Test
    fun `holds ECS credentials to the trusted ecosystems of the network`() = runTest {
        val restricted = DEVNET.copy(trustedEcsEcosystemDids = listOf(TRUSTED_ECOSYSTEM))
        val useCase = ResolveVeranaTrustImpl(indexer, listOf(restricted))
        coEvery { indexer.resolve(restricted, DID) } returns resolved(trusted = true, network = restricted)

        coEvery { indexer.fetchEcosystemDid(restricted, ECOSYSTEM_ID) } returns TRUSTED_ECOSYSTEM
        assertEquals(VeranaTrustStatus.TRUSTED, useCase(DID).status)

        coEvery { indexer.fetchEcosystemDid(restricted, ECOSYSTEM_ID) } returns "did:webvh:QmRogue:rogue.example"
        val rogue = ResolveVeranaTrustImpl(indexer, listOf(restricted))(DID)
        assertEquals(VeranaTrustStatus.UNTRUSTED, rogue.status)
        assertEquals(VeranaUntrustedReason.ECOSYSTEM_NOT_TRUSTED, rogue.reason)

        coEvery { indexer.fetchEcosystemDid(restricted, ECOSYSTEM_ID) } returns null
        assertEquals(VeranaTrustStatus.UNVERIFIED, ResolveVeranaTrustImpl(indexer, listOf(restricted))(DID).status)
    }

    @Test
    fun `a TRUSTED resolution is reused for a minute`() = runTest {
        val clock = MutableClock()
        val useCase = ResolveVeranaTrustImpl(indexer, listOf(DEVNET), clock)
        coEvery { indexer.resolve(DEVNET, DID) } returns resolved(trusted = true, network = DEVNET)

        useCase(DID)
        clock.advanceBy(Duration.ofSeconds(59))
        assertEquals(VeranaTrustStatus.TRUSTED, useCase(DID).status)
        coVerify(exactly = 1) { indexer.resolve(DEVNET, DID) }

        clock.advanceBy(Duration.ofSeconds(1))
        useCase(DID)
        coVerify(exactly = 2) { indexer.resolve(DEVNET, DID) }
    }

    @Test
    fun `an untrusted resolution is never reused`() = runTest {
        val useCase = ResolveVeranaTrustImpl(indexer, listOf(DEVNET))
        coEvery { indexer.resolve(DEVNET, DID) } returns VeranaIndexerAnswer.Unanswered

        assertEquals(VeranaTrustStatus.UNVERIFIED, useCase(DID).status)
        coEvery { indexer.resolve(DEVNET, DID) } returns resolved(trusted = true, network = DEVNET)

        assertEquals(VeranaTrustStatus.TRUSTED, useCase(DID).status)
        coVerify(exactly = 2) { indexer.resolve(DEVNET, DID) }
    }

    private class MutableClock(private var now: Instant = Instant.parse("2026-10-08T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advanceBy(duration: Duration) {
            now = now.plus(duration)
        }
    }

    private fun resolved(trusted: Boolean, network: VeranaNetwork) = VeranaIndexerAnswer.Resolved(
        VeranaTrustResolution(
            did = DID,
            status = if (trusted) VeranaTrustStatus.TRUSTED else VeranaTrustStatus.UNTRUSTED,
            reason = if (trusted) null else VeranaUntrustedReason.NOT_TRUSTED,
            network = network,
            credentials = listOf(
                VeranaTrustCredential(ecsSchema = "ServiceCredential", ecosystemId = ECOSYSTEM_ID, claims = emptyList()),
            ),
        )
    )

    private companion object {
        const val DID = "did:webvh:QmService:service.example"
        const val TRUSTED_ECOSYSTEM = "did:webvh:QmEcs:ecs.example"
        const val ECOSYSTEM_ID = 3L
        val DEVNET = VeranaNetwork(
            id = "vna-devnet-1",
            name = "Devnet",
            indexerUrl = "https://idx.devnet.example",
            production = false,
        )
        val TESTNET = VeranaNetwork(
            id = "vna-testnet-1",
            name = "Testnet",
            indexerUrl = "https://idx.testnet.example",
            production = false,
        )
    }
}

package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import org.mockito.kotlin.never
import kotlin.test.fail
import kotlin.test.junit5.JUnit5Asserter.assertEquals

class DefaultAssetServiceTest {

    private val engine: AssetSelectionEngine = mock()
    private val provider: AssetProvider = mock()
    private val service = DefaultAssetService(engine, provider)

    // Use the REAL engine here instead of a mock
    private val greedyEngine = GreedyEngine()
    private val greedyService = DefaultAssetService(greedyEngine, provider)

    @Test
    fun `should return selected assets from greedy engine after filtering by date`() {
        val targetDate = LocalDate.of(2025, 12, 29)
        val assets = listOf(
            Asset("EXPENSIVE", "Expensive", 1000.0, listOf(targetDate), 50),
            Asset("CHEAP", "Cheap", 100.0, listOf(targetDate), 50),
            Asset("WRONG-DATE", "Wrong Date", 10.0, listOf(LocalDate.now()), 100)
        )

        whenever(provider.allAssets).thenReturn(assets)

        val result = greedyService.findAssets(targetDate, 40)

        when(result){
            is SelectionResult.Success -> {
                assertEquals("Should only pick one asset",1, result.assets.size )
                assertEquals("Should have picked the cheaper asset based on greedy logic",
                    "CHEAP", result.assets[0].code)
            }
            is SelectionResult.Failure -> {
                fail("Expected success but got failure: ${result.reason}")
            }
        }
    }

    @Test
    fun `should filter assets by date before passing to engine`() {
        val targetDate = LocalDate.of(2025, 12, 29)
        val otherDate = LocalDate.of(2025, 12, 30)

        val availableAsset = Asset("OK", "Available", 10.0, listOf(targetDate), 100)
        val busyAsset = Asset("NO", "Busy", 20.0, listOf(otherDate), 200)

        whenever(provider.allAssets).thenReturn(listOf(availableAsset, busyAsset))

        service.findAssets(targetDate, 100)

        verify(engine).selectAssets(100, listOf(availableAsset))
    }

    @Test
    fun `Empty asset list returns error message without calling the engine`() {
        val date = LocalDate.now()
        val volume = 100
        val rawAssets = emptyList<Asset>()

        whenever(provider.allAssets).thenReturn(rawAssets)

        service.findAssets(date, volume)

        verify(provider).allAssets
        verify(engine, never()).selectAssets(volume, rawAssets)
    }
}
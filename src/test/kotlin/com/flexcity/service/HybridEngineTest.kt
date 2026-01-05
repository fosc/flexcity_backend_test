package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import kotlin.test.assertEquals

class HybridEngineTest {

    private val greedyEngine = GreedyEngine()
    private val dpEngine = DynamicProgrammingEngine()
    private val hybridEngine = HybridEngine()

    @ParameterizedTest(name = "{0}") // {0} uses the first argument as the test name
    @MethodSource("provideSelectionScenarios")
    fun `should select correct assets for various scenarios`(
        scenarioName: String,
        targetVolume: Int,
        inputAssets: List<Asset>,
        expectedCodes: List<String>,
        isSuccess: Boolean = true
    ) {
        val result = hybridEngine.selectAssets(targetVolume, inputAssets)
        var success = false
        var resultCodes = emptyList<String>()
        when (result) {
            is SelectionResult.Success -> {
                resultCodes = result.assets.map { it.code }
                success = true
            }
            is SelectionResult.Failure -> {}
        }
        assertEquals(isSuccess, success, "Failed scenario: $scenarioName")
        assertEquals(expectedCodes.sorted(), resultCodes.sorted(), "Failed scenario: $scenarioName")
        println("Passed scenario: $scenarioName")
    }

    companion object {
        @JvmStatic
        fun provideSelectionScenarios(): List<Arguments> {
            return listOf(
                Arguments.of(
                    "All 2 assets needed",
                    150,
                    listOf(
                        Asset("A", "A", 10.0, emptyList(), 100),
                        Asset("B", "B", 10.0, emptyList(), 100)
                    ),
                    listOf("A", "B"),
                    true
                ),
                Arguments.of(
                    "Non optimal sub solution required in optimal solution",
                    5,
                    listOf(
                        Asset("A", "A", 2.44, emptyList(), 2),
                        Asset("B", "B", 2.19, emptyList(), 3),
                        Asset("C", "C", 1.17, emptyList(), 2),
                        Asset("D", "D", 1.0, emptyList(), 1)

                    ),
                    listOf("B", "C"),
                    true
                ),
                Arguments.of(
                    "Dedupe logic test",
                    16,
                    listOf(
                        Asset("A", "A", 5.0, emptyList(), 5),
                        Asset("B", "B", 4.0, emptyList(), 5),
                        Asset("E", "E", 3.0, emptyList(), 7),
                        Asset("F", "F", 3.0, emptyList(), 5)

                        ),
                    listOf("F", "B", "E"),
                    true
                ),
                Arguments.of(
                    "List of length 1",
                    100,
                    listOf(Asset("A", "A", 10.0, emptyList(), 150)),
                    listOf("A"),
                    true
                ),
                Arguments.of(
                    "Pick cheapest cost between two options",
                    100,
                    listOf(
                        Asset("EXPENSIVE", "E", 1000.0, emptyList(), 100), // $10/unit
                        Asset("CHEAP", "C", 100.0, emptyList(), 100)       // $1/unit
                    ),
                    listOf("CHEAP"),
                    true
                ),
                Arguments.of(
                    "Greedy logic failure case",
                    50,
                    listOf(
                        Asset("SMALL", "S", 10.0, emptyList(), 60),
                        Asset("HUGE", "H", 100.0, emptyList(), 1000)
                    ),
                    listOf("SMALL"),
                    true
                ),
                Arguments.of(
                    "Long List of Assets",
                    150,
                    listOf(
                        Asset("A", "A", 10.0, emptyList(), 100),
                        Asset("B", "B", 10.0, emptyList(), 10),
                        Asset("C", "C", 10.0, emptyList(), 10),
                        Asset("G", "G", 100.0, emptyList(), 50),
                        Asset("H", "H", 100.0, emptyList(), 1000),
                        Asset("I", "I", 100.0, emptyList(), 10000),
                        Asset("J", "J", 100.0, emptyList(), 100000),
                        Asset("D", "D", 10.0, emptyList(), 10),
                        Asset("E", "E", 10.0, emptyList(), 10),
                        Asset("F", "F", 10.0, emptyList(), 10)
                    ),
                    listOf("A", "B", "C", "D", "E", "F"),
                    true
                ),
                Arguments.of(
                    "Dedup logic test number 2",
                    15,
                    listOf(
                        Asset("A", "A", 1.0, emptyList(), 5),
                        Asset("B", "B", 1.0, emptyList(), 5),
                        Asset("E", "E", 1.0, emptyList(), 5),
                        Asset("C", "C", 10.0, emptyList(), 9),
                        Asset("D", "D", 100.0, emptyList(), 10)
                    ),
                    listOf("A", "B", "E"),
                    true
                ),Arguments.of(
                    "Overshoot and backtrack correctly",
                    50000,
                    listOf(
                        Asset("A", "A", 23270.0, emptyList(), 16129),
                        Asset("B", "B", 30671.0, emptyList(), 50000),
                        Asset("E", "E", 29743.0, emptyList(), 43011),
                        Asset("C", "C", 397110.0, emptyList(), 404839),
                        Asset("D", "D", 381613.0, emptyList(), 486022)
                    ),
                    listOf("B"),
                    true
                ),
                Arguments.of(
                    "Empty list of assets",
                    8,
                    listOf<Asset>(),
                    listOf<Asset>(),
                    false
                ),
                Arguments.of(
                    "Large volume - hybrid threshold test",
                    150000,
                    listOf(
                        Asset("A", "A", 100.0, emptyList(), 50000),
                        Asset("B", "B", 120.0, emptyList(), 60000),
                        Asset("C", "C", 90.0, emptyList(), 50000),
                        Asset("D", "D", 200.0, emptyList(), 100000)
                    ),
                    listOf("A", "C", "B"),
                    true
                )
            )
        }
    }

    @Test
    fun `hybrid should perform better than or equal to greedy`() {
        var seed = 0
        val noel = LocalDate.of(2025, 12, 25)

        while (seed < 1000) {

            val assets = generateAssets(20, noel, totalKWTarget = 200000, seed=seed)
            val targetVolume = 150000

            val greedyRes = greedyEngine.selectAssets(targetVolume, assets)
            val hybridRes = hybridEngine.selectAssets(targetVolume, assets)

            when{
                hybridRes is SelectionResult.Failure -> fail("Hybrid engine failed with reason: ${hybridRes.reason}")
                greedyRes is SelectionResult.Failure -> fail("Greedy engine failed with reason: ${greedyRes.reason}")
                else -> {
                    val greedyCost = (greedyRes as SelectionResult.Success).assets.sumOf { it.activationCost }
                    val hybridCost = (hybridRes as SelectionResult.Success).assets.sumOf { it.activationCost }

                    // Hybrid should be at least as good as greedy
                    if (hybridCost > greedyCost ) {
                        println("Greedy cost: $greedyCost, Hybrid cost: $hybridCost")
                        fail("Hybrid performed significantly worse than greedy at seed $seed")
                    }
                }

            }
            seed++
            if (seed % 100 == 0){
                println("Tested $seed seeds...")
            }
        }
    }

    @Test
    fun `hybrid should match DP for small volumes`() {
        val noel = LocalDate.of(2025, 12, 25)
        var seed = 0

        while (seed < 500) {
            val assets = generateAssets(15, noel, totalKWTarget = 50000, seed=seed)
            val targetVolume = 10000  // Below hybrid threshold

            val dpRes = dpEngine.selectAssets(targetVolume, assets)
            val hybridRes = hybridEngine.selectAssets(targetVolume, assets)

            when {
                dpRes is SelectionResult.Failure && hybridRes is SelectionResult.Failure -> {
                    // Both failed, that's fine
                }
                dpRes is SelectionResult.Success && hybridRes is SelectionResult.Success -> {
                    val dpCost = dpRes.assets.sumOf { it.activationCost }
                    val hybridCost = hybridRes.assets.sumOf { it.activationCost }

                    // Should match since below threshold means pure DP
                    assertEquals(dpCost, hybridCost, 0.01,
                        "Hybrid should match DP for volumes below threshold at seed $seed")
                }
                else -> {
                    fail("One engine succeeded while other failed at seed $seed")
                }
            }
            seed++
            if (seed % 100 == 0){
                println("Tested $seed seeds for small volumes...")
            }
        }
    }
}

package com.flexcity.service

import com.flexcity.model.SelectionResult
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class AssetSelectionEnginePerformanceTest {

    private val greedyEngine = GreedyEngine()
    private val dpEngine = DynamicProgrammingEngine()
    private val hybridEngine = HybridEngine()

    data class TestCase(
        val name: String,
        val numAssets: Int,
        val targetVolume: Int,
        val totalKWTarget: Int,
        val basePriceFactor: Double = 1.0,
        val seed: Int = 0,
        val excludeDP: Boolean = false,
    )

    data class EngineResult(
        val engineName: String,
        val executionTime: Long,
        val result: SelectionResult
    )

    @Test
    fun `find cases where DP beats Greedy significantly`() {
        val noel = LocalDate.of(2025, 12, 25)
        var biggestDifference = 0.0
        var bestSeed = 0

        for (seed in 0..100000) {
            val assets = generateAssets(20, noel, totalKWTarget = 100000, seed = seed)
            val targetVolume = 50000

            val greedyRes = greedyEngine.selectAssets(targetVolume, assets) // hybridEngine
            val dpRes = dpEngine.selectAssets(targetVolume, assets)

            if (greedyRes is SelectionResult.Success && dpRes is SelectionResult.Success) {
                val greedyCost = greedyRes.assets.sumOf { it.activationCost }
                val dpCost = dpRes.assets.sumOf { it.activationCost }
                val difference = greedyCost - dpCost
                val percentDiff = (difference / dpCost) * 100

                if (percentDiff > biggestDifference) {
                    biggestDifference = percentDiff
                    bestSeed = seed
                    println("Seed $seed: Greedy=$greedyCost, DP=$dpCost, Diff=${percentDiff.roundToInt()}%")
                }
            }

            if (seed % 1000 == 0) println("Tested $seed seeds...")
        }

        println("\nBest case: Seed $bestSeed with ${biggestDifference.roundToInt()}% difference")
    }


    @Test
    fun `test suite - small to large scenarios`() {
        runTestSuite(listOf(
            TestCase(
                name = "Poor greedy performance",
                numAssets = 20,
                targetVolume = 50000,
                totalKWTarget = 100000,
                seed = 5528),
            TestCase(
                name = "Small - 100 assets, 5k target",
                numAssets = 100,
                targetVolume = 5_000,
                totalKWTarget = 50_000,
                seed = 0
            ),
            TestCase(
                name = "Medium - 1k assets, 50k target",
                numAssets = 1_000,
                targetVolume = 50_000,
                totalKWTarget = 500_000,
                seed = 0
            ),
            TestCase(
                name = "Medium - 1k assets, 100k target",
                numAssets = 1000,
                targetVolume = 100_000,
                totalKWTarget = 500_000,
                seed = 0
            ),
            TestCase(
                name = "Medium - 3k assets, 100k target",
                numAssets = 3000,
                targetVolume = 100_000,
                totalKWTarget = 500_000,
                seed = 0
            ),
            TestCase(
                name = "Large - 3k assets, 500k target",
                numAssets = 3000,
                targetVolume = 500_000,
                totalKWTarget = 1_000_000,
                seed = 0
            ),
            TestCase(
                name = "Large - 3k assets, 1GW target",
                numAssets = 3000,
                targetVolume = 1_000_000,
                totalKWTarget = 5_000_000,
                seed = 0
            ),
            TestCase(
                name = "Large - 30k assets, 500k target",
                numAssets = 30_000,
                targetVolume = 1_000_000,
                totalKWTarget = 10_000_000,
                seed = 0,
                excludeDP = true // DP runs out of memory
            )
        ))
    }

    /**
     * Runs a benchmark comparing all three engines and prints detailed results
     */
    private fun runBenchmark(
        testCaseName: String,
        assets: List<com.flexcity.model.Asset>,
        targetVolume: Int,
        includeGreedy: Boolean = true,
        includeDP: Boolean = true,
        includeHybrid: Boolean = true
    ) {
        println("\n========================================")
        println("Test Case: $testCaseName")
        println("========================================")
        println("Number of assets: ${assets.size}")
        println("Total available volume: ${assets.sumOf { it.volume }} kW")
        println("Target volume: $targetVolume kW")
        println()

        val results = mutableListOf<EngineResult>()

        if (includeDP) {
            var dpRes: SelectionResult
            val dpTime = measureTimeMillis {
                dpRes = dpEngine.selectAssets(targetVolume, assets)
            }
            results.add(EngineResult("DP", dpTime, dpRes))
        }

        if (includeGreedy) {
            var greedyRes: SelectionResult
            val greedyTime = measureTimeMillis {
                greedyRes = greedyEngine.selectAssets(targetVolume, assets)
            }
            results.add(EngineResult("Greedy", greedyTime, greedyRes))
        }

        if (includeHybrid) {
            var hybridRes: SelectionResult
            val hybridTime = measureTimeMillis {
                hybridRes = hybridEngine.selectAssets(targetVolume, assets)
            }
            results.add(EngineResult("Hybrid", hybridTime, hybridRes))

        }

        printResultsSummary(results)
    }

    /**
     * Runs multiple test cases in sequence
     */
    private fun runTestSuite(testCases: List<TestCase>) {
        val noel = LocalDate.of(2025, 12, 25)

        testCases.forEach { testCase ->
            val assets = generateAssets(
                testCase.numAssets,
                noel,
                totalKWTarget = testCase.totalKWTarget,
                basePriceFactor = testCase.basePriceFactor,
                seed = testCase.seed
            )

            runBenchmark(
                testCaseName = testCase.name,
                assets = assets,
                targetVolume = testCase.targetVolume,
                includeDP = !testCase.excludeDP
            )
        }
    }

    private fun printResultsSummary(results: List<EngineResult>) {
        println("Results:")
        println("-".repeat(80))
        println("%-15s | %12s | %10s | %12s | %15s".format(
            "Engine", "Time (ms)", "Assets", "Volume (kW)", "Cost (€)"
        ))
        println("-".repeat(80))

        results.forEach { engineResult ->
            when (val result = engineResult.result) {
                is SelectionResult.Success -> {
                    val totalVolume = result.assets.sumOf { it.volume }
                    val totalCost = result.assets.sumOf { it.activationCost }
                    val uniqueAssets = result.assets.distinctBy { it.code }.size

                    println("%-15s | %12d | %10d | %,12d | %,15.2f".format(
                        engineResult.engineName,
                        engineResult.executionTime,
                        uniqueAssets,
                        totalVolume,
                        totalCost
                    ))
                }
                is SelectionResult.Failure -> {
                    println("%-15s | %12d | %10s | %12s | %15s".format(
                        engineResult.engineName,
                        engineResult.executionTime,
                        "FAILED",
                        "-",
                        "-"
                    ))
                    println("  Error: ${result.reason}")
                }
            }
        }
        println("-".repeat(80))
        println()
    }
}

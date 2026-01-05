package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.assertEquals

class DynamicProgEngineTest {

    private val engine = DynamicProgrammingEngine()
    private val hybridEngine = HybridEngine()

    // we use the greedy engine as a benchmark to find counterexamples
    private val greedyEngine = GreedyEngine()

    @ParameterizedTest(name = "{0}") // {0} uses the first argument as the test name
    @MethodSource("provideSelectionScenarios")
    fun `should select correct assets for various scenarios`(
        scenarioName: String,
        targetVolume: Int,
        inputAssets: List<Asset>,
        expectedCodes: List<String>,
        isSuccess: Boolean = true
    ) {
        val result = engine.selectAssets(targetVolume, inputAssets)
        val resultHybrid = hybridEngine.selectAssets(targetVolume, inputAssets)
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
                )
            )
        }
    }

    @Test
    fun findCounterExample() {
        var seed = 0
        val noel = LocalDate.of(2025, 12, 25)

        while (seed < 50000) {

            val assets = generateAssets(10, noel, totalKWTarget = 100, seed=seed)
            val targetVolume = 50

            val greedyRes = greedyEngine.selectAssets(targetVolume, assets)
            val dpRes = engine.selectAssets(targetVolume, assets)

            when{
                dpRes is SelectionResult.Failure -> fail("DP engine failed with reason: ${dpRes.reason}")
                greedyRes is SelectionResult.Failure -> fail("Greedy engine failed with reason: ${greedyRes.reason}")
                else -> {
                    val greedyCost = (greedyRes as SelectionResult.Success).assets.sumOf { it.activationCost }
                    val dpCost = (dpRes as SelectionResult.Success).assets.sumOf { it.activationCost }
                    if (dpCost - greedyCost > 0.1) {
                        println("Greedy cost: $greedyCost, DP cost: $dpCost")
                        fail("Found counterexample at seed $seed")
                    }
                }

            }
            seed++
            if (seed % 1000 == 0){
                println("Tested $seed seeds...")
            }
        }
    }
}


/**
 * Notes:
 * 1/3 of assets will be available @param today. The rest will be available the day before or after.
 * The size of individual assets is scaled so that the entire list approximates the @param totalKWTarget.
 * Thus, increasing just the number of assets will result in smaller assets (fewer kw per asset)
 * 50% of assets are small (10-100 kW before scaling).
 * 45% are medium (100-1000 kW before scaling).
 * 5% are large (1000-5000 kW before scaling).
 * The asset price has +/- 50% evenly distributed random noise.
 *
 *
 * @param count The number of assets to generate.
 * @param today The base date used for determining asset availability.
 * @param totalKWTarget The total target volume (in kilowatts) for all generated assets. Default is 1,000,000 kW.
 * @param seed The seed for random number generation, ensuring reproducible outputs. Default is 42.
 * @param basePriceFactor The factor by which to scale the asset price. Default is 2.0.
 * @return A list of adjusted `Asset` objects.
 */
fun generateAssets(
    count: Int,
    today: LocalDate,
    totalKWTarget: Int = 1000000,
    seed: Int = 42,
    basePriceFactor: Double = 1.0
): List<Asset> {
    val random = Random(seed)

    val assetList = List(count) { index ->
        // Distribution logic:
        // 50% small (10-100), 45% medium (100-1000), 5% large (1000-5000)
        val volumeRoll = random.nextInt(100)
        val volume = when {
            volumeRoll < 50 -> random.nextInt(10, 101)
            volumeRoll < 95 -> random.nextInt(101, 1001)
            else -> random.nextInt(1001, 5001)
        }

        // -1 --> available yesterday, 0 --> available today, 1--> available tomorrow
        val daysToAdd = random.nextInt(-1, 2).toLong()
        val day = today.plusDays(daysToAdd)
        Asset(
            code = "ASSET-$index",
            name = "Generated Asset $index",
            activationCost = Double.NaN, // calculated in next step
            availability = listOf(day),
            volume = volume
        )
    }

    val factor = totalKWTarget / assetList.sumOf { it.volume }.toDouble()

    val adjustedAssets = assetList.map { asset ->
        asset.copy(
            // we scale asset volume to approximate totalKWTarget
            volume = max(1, (asset.volume.toDouble() * factor).roundToInt()),
            // noise between 0.5 and 1.5
            activationCost = max(1.0, (((asset.volume.toDouble() * factor)*basePriceFactor*(0.5 + random.nextDouble()))*100).roundToInt()/100.0 )
        )
    }

    return adjustedAssets
}
package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import java.util.stream.Stream
import kotlin.test.fail

class GreedyEngineTest {

    private val engine = GreedyEngine()

    @ParameterizedTest(name = "{0}") // {0} uses the first argument as the test name
    @MethodSource("provideSelectionScenarios")
    fun `should select correct assets for various scenarios`(
        scenarioName: String,
        targetVolume: Int,
        inputAssets: List<Asset>,
        expectedCodes: List<String>
    ) {
        val result = engine.selectAssets(targetVolume, inputAssets)
        when(result){
            is SelectionResult.Failure -> fail(result.reason)
            is SelectionResult.Success -> {
                val resultCodes = result.assets.map{ it.code }
                assertEquals(expectedCodes, resultCodes, "Failed scenario: $scenarioName")
            }
        }
    }

    companion object {
        @JvmStatic
        fun provideSelectionScenarios(): List<Arguments> {
            return listOf(
            Arguments.of(
                "Simple selection",
                100,
                listOf(Asset("A", "A", 10.0, emptyList(), 150)),
                listOf("A")
            ),
            Arguments.of(
                "Pick cheapest efficiency first",
                100,
                listOf(
                    Asset("EXPENSIVE", "E", 1000.0, emptyList(), 100), // $10/unit
                    Asset("CHEAP", "C", 100.0, emptyList(), 100)       // $1/unit
                ),
                listOf("CHEAP")
            ),
            Arguments.of(
                "Multiple assets needed",
                150,
                listOf(
                    Asset("A", "A", 10.0, emptyList(), 100),
                    Asset("B", "B", 10.0, emptyList(), 100)
                ),
                listOf("A", "B")
            ),
            Arguments.of(
                "Long List of Assets",
                150,
                listOf(
                    Asset("A", "A", 10.0, emptyList(), 100),
                    Asset("B", "B", 10.0, emptyList(), 10),
                    Asset("C", "C", 10.0, emptyList(), 10),
                    Asset("D", "D", 10.0, emptyList(), 10),
                    Asset("E", "E", 10.0, emptyList(), 10),
                    Asset("F", "F", 10.0, emptyList(), 10),
                    Asset("G", "G", 10.0, emptyList(), 100),
                    Asset("H", "H", 10.0, emptyList(), 1000),
                    Asset("I", "I", 10.0, emptyList(), 10000),
                    Asset("J", "J", 10.0, emptyList(), 100000)
                ),
                listOf("J")
            ),
            Arguments.of(
                "Long List of Assets",
                8,
                listOf(
                    Asset("A", "A", 1.0, emptyList(), 1),
                    Asset("B", "B", 1.0, emptyList(), 1),
                    Asset("C", "C", 10.0, emptyList(), 9),
                    Asset("D", "D", 100.0, emptyList(), 10)
                ),
                listOf("C")
            ),
            Arguments.of(
                "Empty list of assets",
                8,
                listOf<Asset>(),
                listOf<Asset>()
            )
            )
        }
    }
}
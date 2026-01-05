package com.flexcity.service

import com.flexcity.model.Asset
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@Component
class AssetProvider(
    @Value($$"${asset.provider.count:1500}") private val assetCount: Int,
    @Value($$"${asset.provider.total-kw-target:1000000}") private val totalKWTarget: Int,
    @Value($$"${asset.provider.seed:0}") private val seed: Int,
    @Value($$"${asset.provider.base-price-factor:2.0}") private val basePriceFactor: Double
) {
    val allAssets = generateAssets(
        count = assetCount,
        today = LocalDate.now(),
        totalKWTarget = totalKWTarget,
        seed = seed,
        basePriceFactor = basePriceFactor
    )

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
    private fun generateAssets(
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

}
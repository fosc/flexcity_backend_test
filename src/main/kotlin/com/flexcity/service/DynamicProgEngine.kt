package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
@ConditionalOnProperty(name = ["selection.engine"], havingValue = "dp", matchIfMissing = false)
class DynamicProgrammingEngine : AssetSelectionEngine {

    // we are creating a linked list with this data structure
    private class Selection(val asset: Asset, val parent: Selection?)

    /**
     * Selects an optimal combination of assets to meet a target volume at minimum cost
     * using a 0/1 Knapsack-style Dynamic Programming approach.
     *
     * The algorithm builds a cost table where each index represents a specific volume (kW),
     * storing the minimum activation cost found so far to reach that volume.
     *
     * @param targetVolume The minimum required volume (in kW) to be fulfilled.
     * @param allAssets The list of candidate assets available for selection.
     * @return A [SelectionResult.Success] containing the list of assets that minimize cost
     *         while meeting or exceeding [targetVolume], or a [SelectionResult.Failure]
     *         if the total available volume is insufficient.
     */
    override fun selectAssets(targetVolume: Int, allAssets: List<Asset>): SelectionResult {
        if (targetVolume <= 0 || allAssets.isEmpty()){
            return SelectionResult.Failure("No assets available", errorCode = 422)
        }
        // In dp[v] we store the minimum cost needed to achieve at least v kW.
        val dp = DoubleArray(targetVolume + 1) { Double.POSITIVE_INFINITY }
        // dp[0] is set to 0.0 because we can get a 0 kW reduction for free (no assets required).
        dp[0] = 0.0

        // To reconstruct the list of assets after the optimal cost is found
        val solutionTracker: Array<Selection?> = arrayOfNulls<Selection>(targetVolume + 1)

        // Each time we iterate on a new asset, we keep track of all cheaper solutions
        for (asset in allAssets) {
            val vol = asset.volume
            val cost = asset.activationCost

            // We iterate backwards for a space-optimized solution
            for (v in targetVolume-1 downTo 0) {

                if (dp[v] == Double.POSITIVE_INFINITY) continue

                // targetVolume is the maximum because all solutions that exceed the target volume
                // are equivalent (we don't care how much we overshoot).
                val nextV = min(targetVolume, v + vol)

                val nextCost = dp[v] + cost

                // If the new combination of assets is cheaper for the same kWs,
                // then we will replace the old solution with this new cheaper one
                if (nextCost < dp[nextV]) {
                    dp[nextV] = nextCost
                    solutionTracker[nextV] = Selection(asset, solutionTracker[v])
                }
            }
        }

        if (dp[targetVolume] == Double.POSITIVE_INFINITY){
            // there were not enough assets to reach the target volume
            return SelectionResult.Failure("Insufficient assets to meet target volume", errorCode = 422)
        }
        // Backtrack through the parentTracker to find which assets were picked
        val selectedAssets = mutableListOf<Asset>()
        var solution = solutionTracker[targetVolume]
        while (solution != null) {
            selectedAssets.add(solution.asset)
            solution = solution.parent
        }
        return SelectionResult.Success(selectedAssets)
    }
}
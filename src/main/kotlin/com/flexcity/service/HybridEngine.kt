package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
@ConditionalOnProperty(name = ["selection.engine"], havingValue = "hybrid", matchIfMissing = true)
class HybridEngine : AssetSelectionEngine {

    // we are creating a linked list with this data structure
    private class Selection(val asset: Asset, val parent: Selection?)

    companion object {
        // If target volume exceeds this threshold, use hybrid approach
        private const val HYBRID_THRESHOLD = 100_000
        // Target volume to reduce to before switching to DP
        private const val DP_TARGET_VOLUME = 50_000
    }

    /**
     * Selects an optimal combination of assets using a hybrid approach:
     * 1. For large volumes (> threshold), use greedy algorithm to quickly reduce volume
     * 2. Switch to DP for final optimization on the remaining smaller volume
     *
     * This balances speed (greedy for bulk) with optimality (DP for fine-tuning).
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

        if (targetVolume <= HYBRID_THRESHOLD) {
            return selectAssetsDP(targetVolume, allAssets)
        }

        // Use greedy algorithm to reduce volume from targetVolume to DP_TARGET_VOLUME
        val greedyTarget = targetVolume - DP_TARGET_VOLUME
        val greedyAssets = selectAssetsGreedy(greedyTarget, allAssets)
        val actualDPTarget = targetVolume - greedyAssets.sumOf { it.volume }

        println("actualDPTarget:${actualDPTarget}" )
        if (actualDPTarget <= 0) {
            println("Hybrid engine: Greedy selection met target volume, skipping DP")
            return SelectionResult.Success(greedyAssets)
        }

        // Use DP on remaining assets for the rest of the volume
        val usedAssetIds = greedyAssets.map { it.code }.toSet()
        val remainingAssets = allAssets.filter { it.code !in usedAssetIds }
        val selected = when (val result = selectAssetsDP(actualDPTarget, remainingAssets)) {
            is SelectionResult.Success -> greedyAssets + result.assets
            is SelectionResult.Failure -> return result
        }

        // We sort descending to prioritize the removal of expensive assets (per kw) before cheaper ones.
        var currentVolume = selected.sumOf { it.volume }
        val optimizedList = selected.sortedByDescending { it.activationCost }.toMutableList()
        val iterator = optimizedList.iterator()
        while (iterator.hasNext()) {
            val asset = iterator.next()
            if (currentVolume - asset.volume >= targetVolume) {
                currentVolume -= asset.volume
                iterator.remove()
            }
        }

        var pureGreedySelection = selectAssetsGreedy(targetVolume, allAssets)

        return if (pureGreedySelection.sumOf { it.activationCost } < optimizedList.sumOf { it.activationCost }) {
            SelectionResult.Success(pureGreedySelection)
        } else SelectionResult.Success(optimizedList)
    }

    /**
     * Greedy selection - sorts by cost efficiency and selects until the target is met
     */
    private fun selectAssetsGreedy(targetVolume: Int, allAssets: List<Asset>): List<Asset> {
        val sortedAssets = allAssets.sortedBy { it.activationCost / it.volume.toDouble() }
        val selected = mutableListOf<Asset>()
        var currentVolume = 0

        for (asset in sortedAssets) {
            selected.add(asset)
            currentVolume += asset.volume
            if (currentVolume >= targetVolume) break
        }

        // We sort descending to prioritize the removal of expensive assets (per kw) before cheaper ones.
        val optimizedList = selected.sortedByDescending { it.activationCost }.toMutableList()
        val iterator = optimizedList.iterator()
        while (iterator.hasNext()) {
            val asset = iterator.next()
            if (currentVolume - asset.volume >= targetVolume) {
                currentVolume -= asset.volume
                iterator.remove()
            }
        }

        return optimizedList
    }

    /**
     * Dynamic Programming selection
     */
    private fun selectAssetsDP(targetVolume: Int, allAssets: List<Asset>): SelectionResult {
        if (allAssets.isEmpty()) {
            return SelectionResult.Failure("Insufficient assets to meet target volume", errorCode = 422)
        }

        val dp = DoubleArray(targetVolume + 1) { Double.POSITIVE_INFINITY }
        dp[0] = 0.0

        val solutionTracker: Array<Selection?> = arrayOfNulls<Selection>(targetVolume + 1)

        for (asset in allAssets) {
            val vol = asset.volume
            val cost = asset.activationCost

            for (v in targetVolume - 1 downTo 0) {
                if (dp[v] == Double.POSITIVE_INFINITY) continue

                val nextV = min(targetVolume, v + vol)
                val nextCost = dp[v] + cost

                if (nextCost < dp[nextV]) {
                    dp[nextV] = nextCost
                    solutionTracker[nextV] = Selection(asset, solutionTracker[v])
                }
            }
        }

        if (dp[targetVolume] == Double.POSITIVE_INFINITY) {
            return SelectionResult.Failure("Insufficient assets to meet target volume", errorCode = 422)
        }

        val selectedAssets = mutableListOf<Asset>()
        var solution = solutionTracker[targetVolume]
        while (solution != null) {
            selectedAssets.add(solution.asset)
            solution = solution.parent
        }

        return SelectionResult.Success(selectedAssets)
    }
}
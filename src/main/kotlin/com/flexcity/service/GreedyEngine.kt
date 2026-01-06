package com.flexcity.service

import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component


@Component
@ConditionalOnProperty(name = ["selection.engine"], havingValue = "greedy", matchIfMissing = false)
class GreedyEngine : AssetSelectionEngine {
    /**
     * Selects assets using a fast, heuristic-based greedy approach.
     *
     * This implementation prioritizes speed (O(N log N)) over mathematical optimality.
     * It functions in two phases:
     * 1. **Accumulation**: Sorts assets by cost-efficiency (cost/volume) and selects them until
     *    the target is met.
     * 2. **Refinement**: Iteratively attempts to remove expensive assets from the selection
     *    to minimize overshoot while still maintaining the [targetVolume].
     *
     * Note: While significantly faster than Dynamic Programming for large datasets, it may
     * produce sub-optimal costs in scenarios where a specific combination of less efficient
     * assets would result in a tighter fit.
     *
     * @param targetVolume The minimum required volume (in kW) to be fulfilled.
     * @param allAssets The list of candidate assets available for evaluation.
     * @return A [SelectionResult.Success] containing the selected assets, or a
     *         [SelectionResult.Failure] if the total volume of [allAssets] cannot meet the target.
     */
    override fun selectAssets(targetVolume: Int, allAssets: List<Asset>): SelectionResult {

        val sortedAssetsCostPerVolume = allAssets.sortedBy { it.activationCost/it.volume.toDouble() }
        var currentVolume = 0
        val selectedAssets = mutableListOf<Asset>()

        for (asset in sortedAssetsCostPerVolume) {
            selectedAssets += asset
            currentVolume += asset.volume
            if (currentVolume >= targetVolume) break
        }

        // We sort descending to prioritize the removal of expensive assets (per kw) before cheaper ones.
        val optimizedList = selectedAssets.sortedByDescending { it.activationCost }.toMutableList()
        val iterator = optimizedList.iterator()
        while (iterator.hasNext()) {
            val asset = iterator.next()
            if (currentVolume - asset.volume >= targetVolume) {
                currentVolume -= asset.volume
                iterator.remove()
            }
        }

        return SelectionResult.Success(optimizedList)
    }
}
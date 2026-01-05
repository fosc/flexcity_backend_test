package com.flexcity.service
import com.flexcity.model.Asset
import com.flexcity.model.SelectionResult

interface AssetSelectionEngine {
    fun selectAssets(targetVolume: Int, allAssets: List<Asset>): SelectionResult
}

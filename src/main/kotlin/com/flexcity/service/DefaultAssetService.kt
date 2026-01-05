package com.flexcity.service

import com.flexcity.model.SelectionResult
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DefaultAssetService (
    private val selectionEngine: AssetSelectionEngine,
    private val assetProvider: AssetProvider
) : AssetService {
    override fun findAssets(date: LocalDate, volume: Int): SelectionResult {
        if (volume <= 0) return SelectionResult.Failure("Invalid volume", errorCode = 422)
        return try {
            val availableAssets = assetProvider.allAssets.filter { date in it.availability }
            if (availableAssets.isEmpty()) return SelectionResult.Failure("No assets available", errorCode = 422)
            selectionEngine.selectAssets(volume, availableAssets)
        } catch (e: Exception) {
            return SelectionResult.Failure("Error processing assets", errorCode = 500)
        }
    }
}
package com.flexcity.service

import com.flexcity.model.SelectionResult
import java.time.LocalDate

interface AssetService {
    fun findAssets(date: LocalDate, volume: Int): SelectionResult
}
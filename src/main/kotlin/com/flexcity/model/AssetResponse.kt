package com.flexcity.model

import java.time.LocalDate

data class AssetResponse(
    val code: String,
    val name: String,
    val price: Double,
    val availability: List<LocalDate>,
    val volume: Int
)
package com.flexcity.model

import java.time.LocalDate

data class AssetRequest(
    val date: LocalDate,
    val volume: Int
)
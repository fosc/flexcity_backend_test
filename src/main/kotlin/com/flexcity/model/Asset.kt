package com.flexcity.model

import java.time.LocalDate

data class Asset(
    val code: String,
    val name: String,
    val activationCost: Double,
    val availability: List<LocalDate>,
    val volume: Int
)
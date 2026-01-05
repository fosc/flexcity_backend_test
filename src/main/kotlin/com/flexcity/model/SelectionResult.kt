package com.flexcity.model

sealed class SelectionResult {
    data class Success(val assets: List<Asset>) : SelectionResult()
    data class Failure(
        val reason: String,
        val errorCode: Int
    ) : SelectionResult()
}
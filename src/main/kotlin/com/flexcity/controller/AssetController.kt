package com.flexcity.controller

import com.flexcity.model.AssetResponse
import com.flexcity.model.Asset
import com.flexcity.model.AssetRequest
import com.flexcity.model.SelectionResult
import com.flexcity.service.AssetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AssetController (private val assetService: AssetService) {
    @PostMapping("/assets")
    fun getAssets(@RequestBody request: AssetRequest): ResponseEntity<Any> {
        when(val result = assetService.findAssets(request.date, request.volume)){
            is SelectionResult.Failure -> {
                return ResponseEntity.status(result.errorCode).body((mapOf("error" to result.reason)))
            }
            is SelectionResult.Success -> {
                return ResponseEntity.ok(
                    result.assets.map{ asset ->
                    AssetResponse(
                        code = asset.code,
                        name = asset.name,
                        price = asset.activationCost,
                        availability = asset.availability,
                        volume = asset.volume
                    )
                }
                )
            }
        }
    }
}


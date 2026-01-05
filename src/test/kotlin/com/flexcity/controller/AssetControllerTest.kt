package com.flexcity.controller

import com.flexcity.model.Asset
import com.flexcity.model.AssetRequest
import com.flexcity.model.SelectionResult
import com.flexcity.service.AssetService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate


@WebMvcTest(AssetController::class)
class AssetControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var assetService: AssetService

    @Test
    fun `should return mapped assets from service`() {
        val testDate = LocalDate.of(2025, 12, 29)
        val testVolume = 100
        val mockAssets = listOf(
            Asset(
                code = "MOCK-1",
                name = "Mock Asset",
                activationCost = 500.0,
                availability = listOf(testDate),
                volume = 50
            )
        )

        whenever(assetService.findAssets(testDate, testVolume)).thenReturn(SelectionResult.Success(mockAssets))

        val request = AssetRequest(date = testDate, volume = testVolume)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].code").value("MOCK-1"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("Mock Asset"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].price").value(500.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].volume").value(50))
    }

    @Test
    fun `should return empty list when no assets found`() {
        val testDate = LocalDate.now()
        whenever(assetService.findAssets(testDate, 0)).
        thenReturn(SelectionResult.Failure("No assets found", errorCode = 404))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AssetRequest(testDate, 0)))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(0))
    }
}
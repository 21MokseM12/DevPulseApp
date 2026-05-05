package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.ApiErrorKind
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractDtoTest {
    private val moshi: Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Test
    fun clientCredentialsRequest_serializesExpectedFields() {
        val adapter = moshi.adapter(ClientCredentialsRequestDto::class.java)
        val dto =
            ClientCredentialsRequestDto(
                login = "moksem",
                password = "secret",
            )

        val json = adapter.toJson(dto)

        assertEquals("""{"login":"moksem","password":"secret"}""", json)
    }

    @Test
    fun addLinkRequest_serializesWithTagsAndFilters() {
        val adapter = moshi.adapter(AddLinkRequestDto::class.java)
        val dto =
            AddLinkRequestDto(
                link = "https://github.com",
                tags = listOf("dev", "news"),
                filters = listOf("contains:kotlin"),
            )

        val json = adapter.toJson(dto)

        assertTrue(json.contains(""""link":"https://github.com""""))
        assertTrue(json.contains(""""tags":["dev","news"]"""))
        assertTrue(json.contains(""""filters":["contains:kotlin"]"""))
    }

    @Test
    fun apiErrorResponse_deserializesNullableFields() {
        val adapter = moshi.adapter(ApiErrorResponseDto::class.java)
        val json =
            """
            {
              "description": "invalid request",
              "code": "400",
              "exceptionName": "ValidationException",
              "exceptionMessage": "field link is required",
              "stacktrace": ["line1", "line2"]
            }
            """.trimIndent()

        val dto = requireNotNull(adapter.fromJson(json))

        assertEquals("invalid request", dto.description)
        assertEquals("400", dto.code)
        assertEquals("ValidationException", dto.exceptionName)
        assertEquals("field link is required", dto.exceptionMessage)
        assertEquals(listOf("line1", "line2"), dto.stacktrace)
    }

    @Test
    fun mappers_convertDtoToDomainModels() {
        val linkDomain =
            LinkResponseDto(
                id = 42L,
                url = "https://example.com",
                tags = null,
                filters = listOf("contains:java"),
            ).toDomain()
        val errorDomain =
            ApiErrorResponseDto(
                description = "unauthorized",
                code = "401",
                exceptionName = null,
                exceptionMessage = null,
                stacktrace = null,
            ).toDomain()

        assertEquals(42L, linkDomain.id)
        assertEquals("https://example.com", linkDomain.url)
        assertEquals(emptyList<String>(), linkDomain.tags)
        assertEquals(listOf("contains:java"), linkDomain.filters)

        assertEquals(ApiErrorKind.Unknown, errorDomain.kind)
        assertEquals("unauthorized", errorDomain.userMessage)
        assertEquals("401", errorDomain.code)
        assertEquals(null, errorDomain.statusCode)
    }
}

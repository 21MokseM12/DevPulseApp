package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.ApiErrorResponseDto
import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ApiErrorMapperTest {
    private val mapper = ApiErrorMapper()

    @Test
    fun mapsBadRequestStatus() {
        val error =
            mapper.mapApiError(
                statusCode = 400,
                rawError =
                    ApiErrorResponseDto(
                        description = "invalid body",
                        code = "BAD_REQUEST",
                    ),
            )

        assertEquals(ApiErrorKind.BadRequest, error.kind)
        assertEquals("invalid body", error.userMessage)
        assertEquals(400, error.statusCode)
    }

    @Test
    fun mapsNotFoundStatusWithFallbackMessage() {
        val error =
            mapper.mapApiError(
                statusCode = 404,
                rawError = null,
            )

        assertEquals(ApiErrorKind.NotFound, error.kind)
        assertEquals("Запрошенный ресурс не найден.", error.userMessage)
    }

    @Test
    fun mapsServerUnavailableStatusWithDedicatedFallbackMessage() {
        val error =
            mapper.mapApiError(
                statusCode = 503,
                rawError = null,
            )

        assertEquals(ApiErrorKind.Unknown, error.kind)
        assertEquals("Сервер временно недоступен. Попробуйте позже.", error.userMessage)
    }

    @Test
    fun mapsTimeoutAsNetworkTimeout() {
        val error = mapper.mapNetworkError(SocketTimeoutException("timeout"))

        assertEquals(ApiErrorKind.NetworkTimeout, error.kind)
        assertEquals("Превышено время ожидания сети. Повторите попытку.", error.userMessage)
    }

    @Test
    fun mapsIoExceptionAsNetworkError() {
        val error = mapper.mapNetworkError(IOException("no route"))

        assertEquals(ApiErrorKind.Network, error.kind)
        assertEquals("Ошибка сети. Проверьте подключение к интернету.", error.userMessage)
    }

    @Test
    fun mapsUnexpectedThrowableAsUnknown() {
        val error = mapper.mapNetworkError(IllegalStateException("boom"))

        assertEquals(ApiErrorKind.Unknown, error.kind)
        assertEquals("Произошла непредвиденная ошибка. Попробуйте снова.", error.userMessage)
    }

    @Test
    fun redactsSensitiveDataInTechnicalDescription() {
        val apiError =
            mapper.mapApiError(
                statusCode = 400,
                rawError =
                    ApiErrorResponseDto(
                        description = "invalid body",
                        code = "BAD_REQUEST",
                        exceptionMessage =
                            "?login=alice&password=secret&token=abc " +
                                "Authorization: Bearer private Authorization: Basic YWxpY2U6c2VjcmV0",
                    ),
            )
        val networkError = mapper.mapNetworkError(IOException("""{"login":"alice","refresh_token":"my-token"}"""))

        assertEquals(
            "?login=***&password=***&token=*** Authorization: Bearer *** Authorization: Basic ***",
            apiError.technicalDescription,
        )
        assertEquals("""{"login":"***","refresh_token":"***"}""", networkError.technicalDescription)
    }
}

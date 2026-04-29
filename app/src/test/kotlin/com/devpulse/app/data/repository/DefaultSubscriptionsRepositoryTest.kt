package com.devpulse.app.data.repository

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.repository.SubscriptionsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSubscriptionsRepositoryTest {
    @Test
    fun getSubscriptions_mapsDtosToDomain() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult =
                        RemoteCallResult.Success(
                            data =
                                listOf(
                                    LinkResponseDto(
                                        id = 10L,
                                        url = "https://example.org",
                                        tags = listOf("news"),
                                        filters = listOf("contains:kotlin"),
                                    ),
                                ),
                            statusCode = 200,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote)

            val result = repository.getSubscriptions()

            assertTrue(result is SubscriptionsResult.Success)
            val links = (result as SubscriptionsResult.Success).links
            assertEquals(1, links.size)
            assertEquals(10L, links.first().id)
            assertEquals("https://example.org", links.first().url)
        }
    }

    @Test
    fun getSubscriptions_propagatesFailureMessage() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult =
                        RemoteCallResult.ApiFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Некорректный запрос",
                                ),
                            statusCode = 400,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote)

            val result = repository.getSubscriptions()

            assertTrue(result is SubscriptionsResult.Failure)
            assertEquals("Некорректный запрос", (result as SubscriptionsResult.Failure).error.userMessage)
        }
    }

    private class FakeRemoteDataSource(
        private val linksResult: RemoteCallResult<List<LinkResponseDto>>,
    ) : DevPulseRemoteDataSource {
        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> = linksResult

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            throw UnsupportedOperationException()
        }
    }
}

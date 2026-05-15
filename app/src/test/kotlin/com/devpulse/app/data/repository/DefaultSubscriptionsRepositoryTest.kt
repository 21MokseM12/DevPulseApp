package com.devpulse.app.data.repository

import com.devpulse.app.data.local.db.CachedSubscriptionEntity
import com.devpulse.app.data.local.db.SubscriptionsCacheDao
import com.devpulse.app.data.local.db.SubscriptionsSyncStateEntity
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
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
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.getSubscriptions(forceRefresh = true)

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
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Failure)
            assertEquals("Некорректный запрос", (result as SubscriptionsResult.Failure).error.userMessage)
        }
    }

    @Test
    fun getSubscriptions_mapsNullableTagsAndFiltersToEmptyLists() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult =
                        RemoteCallResult.Success(
                            data =
                                listOf(
                                    LinkResponseDto(
                                        id = 11L,
                                        url = "https://example.net",
                                        tags = null,
                                        filters = null,
                                    ),
                                ),
                            statusCode = 200,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Success)
            val link = (result as SubscriptionsResult.Success).links.first()
            assertTrue(link.tags.isEmpty())
            assertTrue(link.filters.isEmpty())
        }
    }

    @Test
    fun getSubscriptions_propagatesNetworkFailureAsFailure() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult =
                        RemoteCallResult.NetworkFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.Network,
                                    userMessage = "Ошибка сети",
                                ),
                            throwable = IllegalStateException("network"),
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Failure)
            assertEquals(ApiErrorKind.Network, (result as SubscriptionsResult.Failure).error.kind)
            assertEquals("Ошибка сети", result.error.userMessage)
        }
    }

    @Test
    fun addSubscription_returnsAddedLink() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult = RemoteCallResult.Success(data = emptyList(), statusCode = 200),
                    addResult =
                        RemoteCallResult.Success(
                            data =
                                LinkResponseDto(
                                    id = 77L,
                                    url = "https://example.com/new",
                                    tags = listOf("dev"),
                                    filters = listOf("contains:kotlin"),
                                ),
                            statusCode = 200,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result =
                repository.addSubscription(
                    link = "https://example.com/new",
                    tags = listOf("dev"),
                    filters = listOf("contains:kotlin"),
                )

            assertTrue(result is SubscriptionsResult.Success)
            assertEquals(77L, (result as SubscriptionsResult.Success).links.first().id)
        }
    }

    @Test
    fun addSubscription_propagatesApiFailure() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult = RemoteCallResult.Success(data = emptyList(), statusCode = 200),
                    addResult =
                        RemoteCallResult.ApiFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Подписка уже существует",
                                ),
                            statusCode = 400,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result =
                repository.addSubscription(
                    link = "https://example.com/new",
                    tags = emptyList(),
                    filters = emptyList(),
                )

            assertTrue(result is SubscriptionsResult.Failure)
            assertEquals("Подписка уже существует", (result as SubscriptionsResult.Failure).error.userMessage)
        }
    }

    @Test
    fun removeSubscription_returnsSuccessWithoutParsingLinkBody() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult = RemoteCallResult.Success(data = emptyList(), statusCode = 200),
                    removeResult =
                        RemoteCallResult.Success(
                            data = BotApiMessageResponseDto(message = "Link removed"),
                            statusCode = 200,
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.removeSubscription("https://example.com/remove")

            assertTrue(result is SubscriptionsResult.Success)
            assertTrue((result as SubscriptionsResult.Success).links.isEmpty())
        }
    }

    @Test
    fun removeSubscription_propagatesFailure() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    linksResult = RemoteCallResult.Success(data = emptyList(), statusCode = 200),
                    removeResult =
                        RemoteCallResult.NetworkFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.Network,
                                    userMessage = "Сеть недоступна",
                                ),
                            throwable = IllegalStateException("network"),
                        ),
                )
            val repository = DefaultSubscriptionsRepository(remote, FakeSubscriptionsCacheDao())

            val result = repository.removeSubscription("https://example.com/remove")

            assertTrue(result is SubscriptionsResult.Failure)
            assertEquals("Сеть недоступна", (result as SubscriptionsResult.Failure).error.userMessage)
        }
    }

    @Test
    fun getSubscriptions_withoutForceRefresh_returnsCachedSnapshotAsStale() {
        runTest {
            val cacheDao =
                FakeSubscriptionsCacheDao(
                    cached =
                        mutableListOf(
                            CachedSubscriptionEntity(
                                id = 3L,
                                url = "https://cached.example",
                                tagsSerialized = "android",
                                filtersSerialized = "contains:room",
                            ),
                        ),
                    syncState =
                        SubscriptionsSyncStateEntity(
                            lastSyncAtEpochMs = 123_456L,
                            isStale = false,
                        ),
                )
            val repository =
                DefaultSubscriptionsRepository(
                    remoteDataSource = FakeRemoteDataSource(linksResult = RemoteCallResult.Success(emptyList(), 200)),
                    subscriptionsCacheDao = cacheDao,
                )

            val result = repository.getSubscriptions(forceRefresh = false)

            assertTrue(result is SubscriptionsResult.Success)
            val success = result as SubscriptionsResult.Success
            assertTrue(success.isStale)
            assertEquals(123_456L, success.lastSyncAtEpochMs)
            assertEquals("https://cached.example", success.links.first().url)
        }
    }

    @Test
    fun getSubscriptions_onNetworkFailure_usesCachedDataAndMarksStateAsStale() {
        runTest {
            val cacheDao =
                FakeSubscriptionsCacheDao(
                    cached =
                        mutableListOf(
                            CachedSubscriptionEntity(
                                id = 10L,
                                url = "https://offline.example",
                                tagsSerialized = "",
                                filtersSerialized = "",
                            ),
                        ),
                    syncState =
                        SubscriptionsSyncStateEntity(
                            lastSyncAtEpochMs = 999L,
                            isStale = false,
                        ),
                )
            val repository =
                DefaultSubscriptionsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            linksResult =
                                RemoteCallResult.NetworkFailure(
                                    error = ApiError(ApiErrorKind.Network, "Ошибка сети"),
                                    throwable = IllegalStateException("offline"),
                                ),
                        ),
                    subscriptionsCacheDao = cacheDao,
                )

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Success)
            val success = result as SubscriptionsResult.Success
            assertTrue(success.isStale)
            assertEquals("https://offline.example", success.links.first().url)
            assertTrue(cacheDao.syncState?.isStale == true)
        }
    }

    @Test
    fun getSubscriptions_forceRefresh_deduplicatesByUrl() {
        runTest {
            val repository =
                DefaultSubscriptionsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            linksResult =
                                RemoteCallResult.Success(
                                    data =
                                        listOf(
                                            LinkResponseDto(
                                                id = 1L,
                                                url = "https://dup.example/path",
                                                tags = listOf("old"),
                                                filters = emptyList(),
                                            ),
                                            LinkResponseDto(
                                                id = 2L,
                                                url = "https://dup.example/path",
                                                tags = listOf("new"),
                                                filters = emptyList(),
                                            ),
                                        ),
                                    statusCode = 200,
                                ),
                        ),
                    subscriptionsCacheDao = FakeSubscriptionsCacheDao(),
                )

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Success)
            val links = (result as SubscriptionsResult.Success).links
            assertEquals(1, links.size)
            assertEquals(2L, links.first().id)
            assertEquals("new", links.first().tags.first())
        }
    }

    @Test
    fun getSubscriptions_forceRefresh_partialSync_resolvesConflictsAndKeepsUniqueLinks() {
        runTest {
            val cacheDao = FakeSubscriptionsCacheDao()
            val repository =
                DefaultSubscriptionsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            linksResult =
                                RemoteCallResult.Success(
                                    data =
                                        listOf(
                                            LinkResponseDto(
                                                id = 10L,
                                                url = "https://keep.example/path",
                                                tags = listOf("keep"),
                                                filters = emptyList(),
                                            ),
                                            LinkResponseDto(
                                                id = 20L,
                                                url = " https://conflict.example/path ",
                                                tags = listOf("old"),
                                                filters = emptyList(),
                                            ),
                                            LinkResponseDto(
                                                id = 21L,
                                                url = "https://CONFLICT.example/path",
                                                tags = listOf("resolved"),
                                                filters = listOf("contains:final"),
                                            ),
                                            LinkResponseDto(
                                                id = 30L,
                                                url = "https://new.example/path",
                                                tags = listOf("new"),
                                                filters = emptyList(),
                                            ),
                                        ),
                                    statusCode = 200,
                                ),
                        ),
                    subscriptionsCacheDao = cacheDao,
                )

            val result = repository.getSubscriptions(forceRefresh = true)

            assertTrue(result is SubscriptionsResult.Success)
            val success = result as SubscriptionsResult.Success
            assertEquals(3, success.links.size)
            assertEquals(10L, success.links[0].id)
            assertEquals(21L, success.links[1].id)
            assertEquals("resolved", success.links[1].tags.first())
            assertEquals(30L, success.links[2].id)
            assertEquals(3, cacheDao.getAll().size)
            assertTrue(cacheDao.syncState?.isStale == false)
        }
    }

    private class FakeRemoteDataSource(
        private val linksResult: RemoteCallResult<List<LinkResponseDto>>,
        private val addResult: RemoteCallResult<LinkResponseDto> =
            RemoteCallResult.Success(
                data =
                    LinkResponseDto(
                        id = 0L,
                        url = "https://example.com",
                        tags = emptyList(),
                        filters = emptyList(),
                    ),
                statusCode = 200,
            ),
        private val removeResult: RemoteCallResult<BotApiMessageResponseDto> =
            RemoteCallResult.Success(
                data = BotApiMessageResponseDto(message = "ok"),
                statusCode = 200,
            ),
    ) : DevPulseRemoteDataSource {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> = linksResult

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> = addResult

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> =
            removeResult

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            throw UnsupportedOperationException()
        }
    }

    private class FakeSubscriptionsCacheDao(
        private val cached: MutableList<CachedSubscriptionEntity> = mutableListOf(),
        var syncState: SubscriptionsSyncStateEntity? = null,
    ) : SubscriptionsCacheDao {
        override suspend fun getAll(): List<CachedSubscriptionEntity> = cached.toList()

        override suspend fun upsertAll(entities: List<CachedSubscriptionEntity>) {
            entities.forEach { entity ->
                cached.removeAll { it.id == entity.id }
                cached.add(entity)
            }
        }

        override suspend fun clearAll() {
            cached.clear()
        }

        override suspend fun replaceAll(entities: List<CachedSubscriptionEntity>) {
            cached.clear()
            cached.addAll(entities)
        }

        override suspend fun getSyncState(id: Int): SubscriptionsSyncStateEntity? = syncState

        override suspend fun upsertSyncState(entity: SubscriptionsSyncStateEntity) {
            syncState = entity
        }
    }
}

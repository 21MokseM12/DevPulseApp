package com.devpulse.app.data.repository

import com.devpulse.app.data.local.db.CachedSubscriptionEntity
import com.devpulse.app.data.local.db.SubscriptionsCacheDao
import com.devpulse.app.data.local.db.SubscriptionsSyncStateEntity
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.toDomain
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSubscriptionsRepository
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
        private val subscriptionsCacheDao: SubscriptionsCacheDao,
    ) : SubscriptionsRepository {
        override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult {
            if (!forceRefresh) {
                val cachedLinks = readCachedLinks()
                if (cachedLinks.isNotEmpty()) {
                    val syncState = subscriptionsCacheDao.getSyncState()
                    return SubscriptionsResult.Success(
                        links = cachedLinks,
                        isStale = true,
                        lastSyncAtEpochMs = syncState?.lastSyncAtEpochMs,
                    )
                }
            }

            return when (val result = remoteDataSource.getLinks()) {
                is RemoteCallResult.Success -> {
                    val now = System.currentTimeMillis()
                    val resolvedLinks = resolveServerConflicts(result.data.map { it.toDomain() })
                    subscriptionsCacheDao.replaceAll(resolvedLinks.map { it.toEntity() })
                    subscriptionsCacheDao.upsertSyncState(
                        SubscriptionsSyncStateEntity(
                            lastSyncAtEpochMs = now,
                            isStale = false,
                        ),
                    )
                    SubscriptionsResult.Success(
                        links = resolvedLinks,
                        isStale = false,
                        lastSyncAtEpochMs = now,
                    )
                }

                is RemoteCallResult.ApiFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }

                is RemoteCallResult.NetworkFailure -> {
                    val cachedLinks = readCachedLinks()
                    if (cachedLinks.isNotEmpty()) {
                        val currentState = subscriptionsCacheDao.getSyncState()
                        subscriptionsCacheDao.upsertSyncState(
                            SubscriptionsSyncStateEntity(
                                lastSyncAtEpochMs = currentState?.lastSyncAtEpochMs,
                                isStale = true,
                            ),
                        )
                        SubscriptionsResult.Success(
                            links = cachedLinks,
                            isStale = true,
                            lastSyncAtEpochMs = currentState?.lastSyncAtEpochMs,
                        )
                    } else {
                        SubscriptionsResult.Failure(error = result.error)
                    }
                }
            }
        }

        override suspend fun addSubscription(
            link: String,
            tags: List<String>,
            filters: List<String>,
        ): SubscriptionsResult {
            return when (
                val result =
                    remoteDataSource.addLink(
                        AddLinkRequestDto(
                            link = link,
                            tags = tags,
                            filters = filters,
                        ),
                    )
            ) {
                is RemoteCallResult.Success -> {
                    val now = System.currentTimeMillis()
                    val addedLink = result.data.toDomain()
                    val updatedCache =
                        resolveServerConflicts(
                            links = listOf(addedLink) + readCachedLinks(),
                        )
                    subscriptionsCacheDao.replaceAll(updatedCache.map { it.toEntity() })
                    subscriptionsCacheDao.upsertSyncState(
                        SubscriptionsSyncStateEntity(
                            lastSyncAtEpochMs = now,
                            isStale = false,
                        ),
                    )
                    SubscriptionsResult.Success(
                        links = listOf(addedLink),
                        isStale = false,
                        lastSyncAtEpochMs = now,
                    )
                }

                is RemoteCallResult.ApiFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }

                is RemoteCallResult.NetworkFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }
            }
        }

        override suspend fun removeSubscription(link: String): SubscriptionsResult {
            return when (val result = remoteDataSource.removeLink(RemoveLinkRequestDto(link = link))) {
                is RemoteCallResult.Success -> {
                    val now = System.currentTimeMillis()
                    val normalizedTarget = normalizeUrl(link)
                    val updatedCache =
                        readCachedLinks()
                            .filterNot { normalizeUrl(it.url) == normalizedTarget }
                    subscriptionsCacheDao.replaceAll(updatedCache.map { it.toEntity() })
                    subscriptionsCacheDao.upsertSyncState(
                        SubscriptionsSyncStateEntity(
                            lastSyncAtEpochMs = now,
                            isStale = false,
                        ),
                    )
                    SubscriptionsResult.Success(
                        links = emptyList(),
                        isStale = false,
                        lastSyncAtEpochMs = now,
                    )
                }

                is RemoteCallResult.ApiFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }

                is RemoteCallResult.NetworkFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }
            }
        }

        private suspend fun readCachedLinks(): List<TrackedLink> {
            return subscriptionsCacheDao.getAll().map { it.toDomain() }
        }

        private fun resolveServerConflicts(links: List<TrackedLink>): List<TrackedLink> {
            val deduplicatedByUrl = linkedMapOf<String, TrackedLink>()
            links.forEach { link ->
                deduplicatedByUrl[normalizeUrl(link.url)] = link
            }
            return deduplicatedByUrl.values.toList()
        }

        private fun normalizeUrl(value: String): String = value.trim().lowercase()

        private fun TrackedLink.toEntity(): CachedSubscriptionEntity {
            return CachedSubscriptionEntity(
                id = id,
                url = url,
                tagsSerialized = tags.joinToString(separator = LIST_SEPARATOR),
                filtersSerialized = filters.joinToString(separator = LIST_SEPARATOR),
            )
        }

        private fun CachedSubscriptionEntity.toDomain(): TrackedLink {
            return TrackedLink(
                id = id,
                url = url,
                tags = tagsSerialized.decodeListField(),
                filters = filtersSerialized.decodeListField(),
            )
        }

        private fun String.decodeListField(): List<String> {
            if (isBlank()) {
                return emptyList()
            }
            return split(LIST_SEPARATOR)
        }

        companion object {
            private const val LIST_SEPARATOR = "\u001F"
        }
    }

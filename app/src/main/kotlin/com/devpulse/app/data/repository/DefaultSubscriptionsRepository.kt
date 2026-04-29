package com.devpulse.app.data.repository

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.toDomain
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSubscriptionsRepository
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
    ) : SubscriptionsRepository {
        override suspend fun getSubscriptions(): SubscriptionsResult {
            return when (val result = remoteDataSource.getLinks()) {
                is RemoteCallResult.Success -> {
                    SubscriptionsResult.Success(links = result.data.map { it.toDomain() })
                }

                is RemoteCallResult.ApiFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }

                is RemoteCallResult.NetworkFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
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
                    SubscriptionsResult.Success(links = listOf(result.data.toDomain()))
                }

                is RemoteCallResult.ApiFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }

                is RemoteCallResult.NetworkFailure -> {
                    SubscriptionsResult.Failure(error = result.error)
                }
            }
        }
    }

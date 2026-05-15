package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.repository.AuthRepository
import com.devpulse.app.domain.repository.AuthResult
import javax.inject.Inject

class RegisterClientUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        suspend operator fun invoke(
            login: String,
            password: String,
        ): AuthResult {
            return authRepository.register(login = login, password = password)
        }
    }

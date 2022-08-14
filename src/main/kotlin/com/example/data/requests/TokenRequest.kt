package com.example.data.requests

import kotlinx.serialization.Serializable

@Serializable
data class TokenRequest(
    val refreshToken: String
)
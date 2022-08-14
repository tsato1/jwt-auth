package com.example.token

data class TokenConfig(
    val issuer: String,
    val audience: String, // means different types, such as amin user, normal user
    val expiresIn: Long,
    val secret: String // only server knows this
)
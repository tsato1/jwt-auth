package com.example.data

class RefreshTokenDatasource {

    val refreshTokens = mutableListOf(
        "soidfownlkjsldkfjaoijekf"
    )

    fun getAllRefreshTokens(): List<String> {
        return refreshTokens
    }

    fun isInDatabase(refreshToken: String): String? {
        return refreshTokens.find { it == refreshToken }
    }

    fun insertRefreshToken(refreshToken: String) {
        refreshTokens.add(refreshToken)
    }

    fun deleteRefreshToken(refreshToken: String) {
        refreshTokens.remove(refreshToken)
    }

}
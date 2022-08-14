package com.example.plugins

import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.token.TokenConfig
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*

fun Application.configureSecurity(accessTokenConfig: TokenConfig, refreshTokenConfig: TokenConfig) {
    authentication {
        jwt("access") {
            realm = this@configureSecurity.environment.config.property("jwt.realm").getString()
            verifier(
                JWT
                    .require(Algorithm.HMAC256(accessTokenConfig.secret))
                    .withAudience(accessTokenConfig.audience)
                    .withIssuer(accessTokenConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.expiresAt.time > System.currentTimeMillis() &&
                    credential.payload.audience.contains(accessTokenConfig.audience)
                ) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }

        jwt("refresh") {
            realm = this@configureSecurity.environment.config.property("jwt.realm").getString()
            verifier(
                JWT
                    .require(Algorithm.HMAC256(refreshTokenConfig.secret))
                    .withAudience(refreshTokenConfig.audience)
                    .withIssuer(refreshTokenConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.expiresAt.time > System.currentTimeMillis() &&
                    credential.payload.audience.contains(refreshTokenConfig.audience)
                ) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}

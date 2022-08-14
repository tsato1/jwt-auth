package com.example.token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

class JwtTokenServiceImpl : TokenService {
    /*
    generates a jwt token
     */
    override fun generateToken(config: TokenConfig, vararg claims: TokenClaim): String {
        var token = if (config.expiresIn == -1L) {
            JWT.create()
                .withAudience(config.audience)
                .withIssuer(config.issuer)
        }
        else {
            JWT.create()
                .withAudience(config.audience)
                .withIssuer(config.issuer)
                .withExpiresAt(Date(System.currentTimeMillis() + config.expiresIn))
        }

        claims.forEach { claim ->
            token = token.withClaim(claim.name, claim.value)
        }

        return token.sign(Algorithm.HMAC256(config.secret))
    }
}
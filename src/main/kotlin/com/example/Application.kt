package com.example

import com.example.data.RefreshTokenDatasource
import com.example.data.User
import com.example.data.UserDatasource
import com.example.data.requests.AuthRequest
import com.example.data.requests.TokenRequest
import com.example.data.responses.AuthResponse
import io.ktor.server.application.*
import com.example.plugins.*
import com.example.token.JwtTokenServiceImpl
import com.example.token.TokenClaim
import com.example.token.TokenConfig
import com.example.token.TokenService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val userDatasource = UserDatasource()

val refreshTokenDatasource = RefreshTokenDatasource()

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {

    val accessTokenConfig = TokenConfig(
        issuer = environment.config.property("jwt.issuer").getString(),
        audience = environment.config.property("jwt.audience").getString(),
        expiresIn = 20000L, // 20 seconds
        secret = System.getenv("JWT_ACCESS_TOKEN_SECRET")
    )
    val refreshTokenConfig = TokenConfig(
        issuer = environment.config.property("jwt.issuer").getString(),
        audience = environment.config.property("jwt.audience").getString(),
        expiresIn = 365L * 1000L * 60L * 60L * 24L, // milli-second equivalent to a year
        secret = System.getenv("JWT_REFRESH_TOKEN_SECRET")
    )

    configureSerialization()
    configureMonitoring()
    configureSecurity(accessTokenConfig, refreshTokenConfig)

    val tokenService = JwtTokenServiceImpl()

    routing {
        signUp()
        signIn(tokenService, accessTokenConfig, refreshTokenConfig)
        refreshAccessToken(tokenService, accessTokenConfig, refreshTokenConfig)
        signOut()
        getSecretInfo()
    }
}

fun Route.signUp() {
    post("signUp") {
        val request = call.receiveOrNull<AuthRequest>() ?: kotlin.run {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val areFieldsBlank = request.email.isBlank() || request.password.isBlank()
        val isPasswordShort = request.password.length < 8
        if (areFieldsBlank || isPasswordShort) {
            call.respond(HttpStatusCode.Conflict, "Invalid email or password")
            return@post
        }

        val user = User(
            email = request.email,
            password = request.password, // TODO hash
            id = request.email.hashCode() // TODO give unique id
        )

        val userExists = userDatasource.getUserByEmail(user.email)
        if (userExists != null) {
            call.respond(HttpStatusCode.Conflict, "User already exists")
            return@post
        }

        userDatasource.insertUser(user)

        userDatasource.getAllUsers().forEach {
            println("${it.id}, ${it.email}, ${it.password}")
        }

        call.respond(HttpStatusCode.OK)
    }
}

fun Route.signIn(
    tokenService: TokenService,
    accessTokenConfig: TokenConfig,
    refreshTokenConfig: TokenConfig
) {
    post("signIn") {
        val request = call.receiveOrNull<AuthRequest>() ?: kotlin.run {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        /* find the user in db */
        val user = userDatasource.getUserByEmail(request.email)
        if (user == null) {
            call.respond(HttpStatusCode.Conflict, "Incorrect email or password")
            return@post
        }

        val isPasswordValid = request.password == user.password
        if (!isPasswordValid) {
            call.respond(HttpStatusCode.Conflict, "Incorrect email or password")
            return@post
        }

        /* accessToken will be stored in the sharedPref on the client side */
        val accessToken = tokenService.generateToken(
            config = accessTokenConfig,
            TokenClaim(
                name = "userId",
                value = user.id.toString()
            )
        )
        val refreshToken = tokenService.generateToken(
            config = refreshTokenConfig,
            TokenClaim(
                name = "userId",
                value = user.id.toString()
            )
        )

        refreshTokenDatasource.insertRefreshToken(refreshToken)

        call.respond(
            status = HttpStatusCode.OK,
            message = AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        )

        refreshTokenDatasource.getAllRefreshTokens().forEachIndexed { i, item ->
            println("$i th refreshToken = $item")
        }
    }
}

fun Route.refreshAccessToken(
    tokenService: TokenService,
    accessTokenConfig: TokenConfig,
    refreshTokenConfig: TokenConfig
) {
    authenticate("refresh") {
        post("refreshAccessToken") {
            val request = call.receiveOrNull<TokenRequest>() ?: kotlin.run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val allRefreshTokens: List<String> = refreshTokenDatasource.getAllRefreshTokens()
            if (!allRefreshTokens.contains(request.refreshToken)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class)
            if (userId == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val existingRefreshToken = refreshTokenDatasource.isInDatabase(request.refreshToken)
            if (existingRefreshToken != null) { // refresh token already exists in db -> delete it first
                refreshTokenDatasource.deleteRefreshToken(request.refreshToken)
            }

            val accessToken = tokenService.generateToken(
                config = accessTokenConfig,
                TokenClaim(
                    name = "userId",
                    value = userId
                )
            )
            val refreshToken = tokenService.generateToken(
                config = refreshTokenConfig,
                TokenClaim(
                    name = "userId",
                    value = userId
                )
            )

            refreshTokenDatasource.insertRefreshToken(refreshToken)

            call.respond(
                status = HttpStatusCode.OK,
                message = AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            )

            refreshTokenDatasource.getAllRefreshTokens().forEachIndexed { i, item ->
                println("$i th refreshToken = $item")
            }
        }
    }
}

fun Route.signOut() {
    authenticate("refresh") {
        delete("signOut") {
            val request = call.receiveOrNull<TokenRequest>() ?: kotlin.run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class)
            if (userId == null) {
                call.respond(HttpStatusCode.Conflict)
                return@delete
            }

            refreshTokenDatasource.deleteRefreshToken(request.refreshToken)

            call.respond(HttpStatusCode.NoContent)

            refreshTokenDatasource.getAllRefreshTokens().forEachIndexed { i, item ->
                println("$i th refreshToken = $item")
            }
        }
    }
}

fun Route.getSecretInfo() {
    authenticate("access") {
        get("secret") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class)
            call.respond(HttpStatusCode.OK, "UserId is $userId")
        }
    }
}

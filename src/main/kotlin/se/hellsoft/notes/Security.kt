package se.hellsoft.notes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class User(val username: String, val password: String)

const val jwtSecret = "secret"
const val jwtIssuer = "http://0.0.0.0:8080/"
const val jwtAudience = "http://0.0.0.0:8080/"
const val jwtRealm = "hellsoft notes"
const val jwtExpiration = 300L

fun Application.configureSecurity() {

    authentication {
        jwt {
            this.realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
        }
    }

    routing {
        post("/login") {
            val user = call.receive<User>()
            val token = JWT.create()
                .withAudience(jwtAudience)
                .withIssuer(jwtIssuer)
                .withClaim("username", user.username)
                .withExpiresAt(Instant.now().plusSeconds(jwtExpiration))
                .sign(Algorithm.HMAC256(jwtSecret))
            call.respond(hashMapOf("token" to token))
        }
    }
}

package se.hellsoft.notes

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.time.Duration
import java.util.Collections

private const val WS_TIMEOUT = 15L

@Serializable
enum class NotificationType { Created, Updated, Deleted }

@Serializable
data class Notification(val type: NotificationType, val note: Note)

fun Application.configureNotesService() {
    setupWebSockets()

    val notesSchema = createNotesSchema()

    val wsClients = Collections.synchronizedSet<DefaultWebSocketServerSession>(LinkedHashSet())

    routing {
//        authenticate {
            post("/notes") {
                val note = call.receive<Note>()
                val createdNote = notesSchema.create(note)
                call.respond(HttpStatusCode.Created, createdNote)
                val notification = Notification(NotificationType.Created, createdNote)
                wsClients.sendNotification(notification)
            }

            get("/notes/{id}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                val note = notesSchema.read(id)

                if (note != null) {
                    call.respond(HttpStatusCode.OK, note)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            put("/notes") {
                val note = call.receive<Note>()
                notesSchema.update(note)
                call.respond(HttpStatusCode.OK)
                wsClients.sendNotification(Notification(NotificationType.Updated, note))
            }

            delete("/notes/{id}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                val note = notesSchema.read(id)
                if (note != null) {
                    notesSchema.delete(id)
                    call.respond(HttpStatusCode.OK)
                    wsClients.sendNotification(Notification(NotificationType.Deleted, note))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            webSocket("/notes/updates") { // websocketSession
                wsClients += this
                receiveDeserialized()
            }
//        }
    }

}

private suspend fun MutableSet<DefaultWebSocketServerSession>.sendNotification(
    notification: Notification
) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val ws = iterator.next()
        try {
            ws.sendSerialized(notification)
        } catch (_: Throwable) {
            iterator.remove()
        }
    }
}

private fun createNotesSchema(): NotesSchema {
    val url = "jdbc:sqlite:notes.db"
    val database = Database.connect(url, driver = "org.sqlite.JDBC")
    return NotesSchema(database)
}

private fun Application.setupWebSockets() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = Duration.ofSeconds(WS_TIMEOUT)
        timeout = Duration.ofSeconds(WS_TIMEOUT)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
package se.hellsoft.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Serializable
data class Note(val id: Int? = null, val title: String, val body: String, val date: Instant)

class NotesSchema(private val database: Database) {
    object Notes : Table() {
        val id = integer("id").autoIncrement()
        val title = varchar("title", length = 50)
        val body = varchar("body", length = 4096)
        val date = varchar("created", length = 30)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Notes)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(note: Note): Note = dbQuery {
        val id = Notes.insert {
            it[title] = note.title
            it[body] = note.body
            it[date] = note.date.format(ISO_DATE_TIME_OFFSET)
        }[Notes.id]

        note.copy(id = id)
    }

    suspend fun search(title: String?): List<Note> {
        return Notes.select { Notes.title.lowerCase().match(title ?: "") }
            .orderBy(Notes.title)
            .map(::mapToNote)
    }

    suspend fun read(id: Int): Note? = dbQuery {
        Notes.select { Notes.id.eq(id) }
            .map(::mapToNote)
            .singleOrNull()
    }

    suspend fun update(note: Note) = dbQuery {
        Notes.update({ Notes.id eq (note.id ?: -1) }) {
            it[title] = note.title
            it[body] = note.body
            it[date] = note.date.format(ISO_DATE_TIME_OFFSET)
        }
    }

    suspend fun delete(id: Int) = dbQuery {
        Notes.deleteWhere { Notes.id.eq(id) }
    }

    private fun mapToNote(it: ResultRow) =
        Note(
            it[Notes.id],
            it[Notes.title],
            it[Notes.body],
            Instant.parse(it[Notes.date], ISO_DATE_TIME_OFFSET)
        )
}

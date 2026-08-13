package com.aura.app.assistant

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts.descending
import com.mongodb.client.model.Updates.set
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Direct, single-user MongoDB storage for backend-free local mode.
 *
 * The database credential is supplied by the user and must be scoped by MongoDB
 * itself to only the selected database and required CRUD operations. Client-side
 * checks are defense in depth; they are not a replacement for database roles.
 */
internal class DirectMongoAssistantStore(
    private val settingsStore: LocalMongoSettingsReader
) : AssistantLocalStore {
    private val clientLock = Any()
    @Volatile private var activeUri: String? = null
    @Volatile private var activeClient: MongoClient? = null

    suspend fun verifyConnection(connectionUri: String, databaseName: String) =
        withContext(Dispatchers.IO) {
            val settings = validateLocalMongoSettings(connectionUri, databaseName)
            MongoClients.create(buildClientSettings(settings.connectionUri)).use { client ->
                client.getDatabase(settings.databaseName).runCommand(Document("ping", 1))
            }
        }

    override suspend fun memories(): List<MemoryResponse> = withCollection(MEMORIES) { collection ->
        collection.find()
            .sort(descending("created_at"))
            .limit(MAX_DOCUMENTS)
            .map(::memoryFromDocument)
            .toList()
    }

    override suspend fun createMemory(title: String, content: String): MemoryResponse =
        withCollection(MEMORIES) { collection ->
            val memory = MemoryResponse(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                created_at = Instant.now().toString()
            )
            collection.insertOne(
                Document("id", memory.id)
                    .append("title", memory.title)
                    .append("content", memory.content)
                    .append("created_at", memory.created_at)
            )
            memory
        }

    override suspend fun todos(): List<TodoResponse> = withCollection(TODOS) { collection ->
        collection.find()
            .sort(descending("created_at"))
            .limit(MAX_DOCUMENTS)
            .map(::todoFromDocument)
            .toList()
    }

    override suspend fun createTodo(title: String): TodoResponse = withCollection(TODOS) { collection ->
        val todo = TodoResponse(
            id = UUID.randomUUID().toString(),
            title = title,
            done = false,
            created_at = Instant.now().toString()
        )
        collection.insertOne(
            Document("id", todo.id)
                .append("title", todo.title)
                .append("done", todo.done)
                .append("created_at", todo.created_at)
        )
        todo
    }

    override suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse =
        withCollection(TODOS) { collection ->
            val document = collection.findOneAndUpdate(
                eq("id", id),
                set("done", done),
                com.mongodb.client.model.FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            ) ?: throw IllegalStateException("Todo not found")
            todoFromDocument(document)
        }

    private suspend fun <T> withCollection(
        name: String,
        block: (com.mongodb.client.MongoCollection<Document>) -> T
    ): T {
        val configured = settingsStore.state.first()
        configured.credentialError?.let { throw IllegalStateException(it) }
        val settings = validateLocalMongoSettings(configured.connectionUri, configured.databaseName)
        val database = clientFor(settings.connectionUri).getDatabase(settings.databaseName)
        return block(database.getCollection(name))
    }

    private fun clientFor(uri: String): MongoClient = synchronized(clientLock) {
        activeClient?.takeIf { activeUri == uri } ?: run {
            activeClient?.close()
            MongoClients.create(buildClientSettings(uri)).also { client ->
                activeUri = uri
                activeClient = client
            }
        }
    }

    private fun memoryFromDocument(document: Document): MemoryResponse = MemoryResponse(
        id = document.getString("id") ?: throw IllegalStateException("Memory is missing id"),
        title = document.getString("title").orEmpty(),
        content = document.getString("content").orEmpty(),
        created_at = document.getString("created_at").orEmpty()
    )

    private fun todoFromDocument(document: Document): TodoResponse = TodoResponse(
        id = document.getString("id") ?: throw IllegalStateException("Todo is missing id"),
        title = document.getString("title").orEmpty(),
        done = document.getBoolean("done", false),
        created_at = document.getString("created_at").orEmpty()
    )

    private fun buildClientSettings(uri: String): MongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .applyToConnectionPoolSettings { builder ->
                builder.maxSize(4).maxConnectionIdleTime(60, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder.connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            }
            .applyToClusterSettings { builder ->
                builder.serverSelectionTimeout(10, TimeUnit.SECONDS)
            }
            .retryWrites(true)
            .build()

    private companion object {
        const val MEMORIES = "aura_memories"
        const val TODOS = "aura_todos"
        const val MAX_DOCUMENTS = 500
    }
}

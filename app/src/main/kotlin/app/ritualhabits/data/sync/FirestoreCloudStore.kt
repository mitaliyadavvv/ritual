package app.ritualhabits.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date

/**
 * Firestore layout (all under the signed-in user's id; see firestore.rules):
 * - users/{uid}/habits/{habitId}
 * - users/{uid}/completions/{habitId}_{yyyy-MM-dd}
 * - users/{uid}/settings/preferences
 * Deleting an account removes all three collections ([deleteAll]).
 * Reads always go to the server ([Source.SERVER]); the local database is the offline copy.
 */
class FirestoreCloudStore(private val firestore: FirebaseFirestore) : CloudStore {

    private fun user(uid: String) = firestore.collection("users").document(uid)

    override suspend fun habitsSince(uid: String, since: Long?): CloudPage<CloudHabit> =
        page(user(uid).collection("habits"), since, ::habitFrom)

    override suspend fun completionsSince(uid: String, since: Long?): CloudPage<CloudCompletion> =
        page(user(uid).collection("completions"), since, ::completionFrom)

    override suspend fun write(uid: String, habits: List<CloudHabit>, completions: List<CloudCompletion>) {
        val root = user(uid)
        // Habits first, so a completion is never visible before its habit.
        val ops = habits.map { root.collection("habits").document(it.id) to habitData(it) } +
            completions.map { root.collection("completions").document("${it.habitId}_${it.date}") to completionData(it) }
        ops.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            call { batch.commit().await() }
        }
    }

    override suspend fun preferences(uid: String): CloudPreferences? {
        val doc = call { user(uid).collection("settings").document("preferences").get(Source.SERVER).await() }
        if (!doc.exists()) return null
        return CloudPreferences(
            themeMode = doc.getString("themeMode") ?: return null,
            hapticsEnabled = doc.getBoolean("hapticsEnabled") ?: true,
            updatedAt = doc.getLong("updatedAt") ?: 0L,
        )
    }

    override suspend fun writePreferences(uid: String, preferences: CloudPreferences) {
        val data = mapOf(
            "themeMode" to preferences.themeMode,
            "hapticsEnabled" to preferences.hapticsEnabled,
            "updatedAt" to preferences.updatedAt,
            "serverUpdatedAt" to FieldValue.serverTimestamp(),
        )
        call { user(uid).collection("settings").document("preferences").set(data).await() }
    }

    override suspend fun deleteAll(uid: String) {
        for (name in listOf("habits", "completions", "settings")) {
            val collection = user(uid).collection(name)
            while (true) {
                val snapshot = call { collection.limit(BATCH_LIMIT.toLong()).get(Source.SERVER).await() }
                if (snapshot.isEmpty) break
                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                call { batch.commit().await() }
            }
        }
    }

    private suspend fun <T> page(collection: CollectionReference, since: Long?, parse: (DocumentSnapshot) -> T?): CloudPage<T> {
        val items = mutableListOf<T>()
        var newest: Long? = null
        var last: DocumentSnapshot? = null
        while (true) {
            var query: Query = collection
            if (since != null) query = query.whereGreaterThanOrEqualTo(SERVER_UPDATED_AT, Timestamp(Date(since)))
            query = query.orderBy(SERVER_UPDATED_AT).limit(PAGE_SIZE.toLong())
            last?.let { query = query.startAfter(it) }
            val snapshot = call { query.get(Source.SERVER).await() }
            for (doc in snapshot.documents) {
                parse(doc)?.let(items::add) // malformed documents are skipped, never crash the app
                doc.getTimestamp(SERVER_UPDATED_AT)?.toDate()?.time?.let { newest = maxOf(newest ?: it, it) }
            }
            if (snapshot.size() < PAGE_SIZE) break
            last = snapshot.documents.last()
        }
        return CloudPage(items, newest)
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw CloudUnavailableException(e)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE || e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
            throw CloudUnavailableException(e)
        }
        throw e
    }

    private fun habitData(h: CloudHabit) = mapOf(
        "name" to h.name,
        "description" to h.description,
        "sortOrder" to h.sortOrder,
        "createdAt" to h.createdAt,
        "archivedAt" to h.archivedAt,
        "deletedAt" to h.deletedAt,
        "reminderEnabled" to h.reminderEnabled,
        "reminderMinutes" to h.reminderMinutes,
        "periods" to h.periods.map { mapOf("start" to it.start, "end" to it.end) },
        "updatedAt" to h.updatedAt,
        SERVER_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun completionData(c: CloudCompletion) = mapOf(
        "habitId" to c.habitId,
        "date" to c.date,
        "done" to c.done,
        "completedAt" to c.completedAt,
        "source" to c.source,
        "focusSessionId" to c.focusSessionId,
        "updatedAt" to c.updatedAt,
        SERVER_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun habitFrom(doc: DocumentSnapshot): CloudHabit? = runCatching {
        CloudHabit(
            id = doc.id,
            name = doc.getString("name")!!,
            description = doc.getString("description"),
            sortOrder = doc.getLong("sortOrder")!!.toInt(),
            createdAt = doc.getLong("createdAt")!!,
            archivedAt = doc.getLong("archivedAt"),
            deletedAt = doc.getLong("deletedAt"),
            reminderEnabled = doc.getBoolean("reminderEnabled") ?: false,
            reminderMinutes = doc.getLong("reminderMinutes")?.toInt(),
            periods = (doc.get("periods") as? List<*>).orEmpty().map {
                val m = it as Map<*, *>
                CloudPeriod(m["start"] as String, m["end"] as String?)
            },
            updatedAt = doc.getLong("updatedAt") ?: 0L,
            serverTime = doc.getTimestamp(SERVER_UPDATED_AT)?.toDate()?.time ?: 0L,
        )
    }.getOrNull()

    private fun completionFrom(doc: DocumentSnapshot): CloudCompletion? = runCatching {
        CloudCompletion(
            habitId = doc.getString("habitId")!!,
            date = doc.getString("date")!!,
            done = doc.getBoolean("done")!!,
            completedAt = doc.getLong("completedAt"),
            source = doc.getString("source"),
            focusSessionId = doc.getString("focusSessionId"),
            updatedAt = doc.getLong("updatedAt") ?: 0L,
            serverTime = doc.getTimestamp(SERVER_UPDATED_AT)?.toDate()?.time ?: 0L,
        )
    }.getOrNull()

    private companion object {
        const val SERVER_UPDATED_AT = "serverUpdatedAt"
        const val PAGE_SIZE = 500
        const val BATCH_LIMIT = 400
        const val TIMEOUT_MS = 20_000L
    }
}

package app.ritualhabits.data.sync

/**
 * The cloud copy of a user's data. Everything lives under the signed-in user's own id, and the
 * backend's access rules (firestore.rules) only let that user read or write it.
 *
 * Every record carries [updatedAt]: the device time of the change it reflects. Conflicts resolve
 * per record, last writer wins. [serverTime] is the backend's commit time, used only as the
 * "what's new since" cursor, because device clocks can't be trusted to be in order.
 */
data class CloudHabit(
    val id: String,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val archivedAt: Long?,
    val deletedAt: Long?,
    val reminderEnabled: Boolean,
    /** Minutes after local midnight; null if never set. */
    val reminderMinutes: Int?,
    /** Eligibility periods, ISO dates; end is exclusive, null = still required. */
    val periods: List<CloudPeriod>,
    val updatedAt: Long,
    val serverTime: Long = 0,
)

data class CloudPeriod(val start: String, val end: String?)

/** One habit day. [done] = false is a tombstone: the day was un-ticked. */
data class CloudCompletion(
    val habitId: String,
    /** Habit day, ISO yyyy-MM-dd: a calendar date, never re-interpreted in another time zone. */
    val date: String,
    val done: Boolean,
    val completedAt: Long?,
    val source: String?,
    val focusSessionId: String?,
    val updatedAt: Long,
    val serverTime: Long = 0,
)

data class CloudPreferences(val themeMode: String, val hapticsEnabled: Boolean, val updatedAt: Long)

/** Records changed at or after a cursor, plus the newest [CloudHabit.serverTime] seen. */
data class CloudPage<T>(val items: List<T>, val newestServerTime: Long?)

interface CloudStore {
    /** Habits the backend committed at or after [since] (all of them when null). */
    suspend fun habitsSince(uid: String, since: Long?): CloudPage<CloudHabit>
    suspend fun completionsSince(uid: String, since: Long?): CloudPage<CloudCompletion>
    /** Upserts by stable id (habit id; habit id + date), so retries never create duplicates. */
    suspend fun write(uid: String, habits: List<CloudHabit>, completions: List<CloudCompletion>)
    suspend fun preferences(uid: String): CloudPreferences?
    suspend fun writePreferences(uid: String, preferences: CloudPreferences)
}

/** Couldn't reach the backend (offline, timeout). Changes stay queued and are retried. */
class CloudUnavailableException(cause: Throwable? = null) : Exception("Backend unreachable", cause)

/** Stand-in when the build has no backend configured; never reached because sign-in isn't offered. */
object UnavailableCloudStore : CloudStore {
    override suspend fun habitsSince(uid: String, since: Long?): CloudPage<CloudHabit> = throw CloudUnavailableException()
    override suspend fun completionsSince(uid: String, since: Long?): CloudPage<CloudCompletion> = throw CloudUnavailableException()
    override suspend fun write(uid: String, habits: List<CloudHabit>, completions: List<CloudCompletion>) = throw CloudUnavailableException()
    override suspend fun preferences(uid: String): CloudPreferences? = throw CloudUnavailableException()
    override suspend fun writePreferences(uid: String, preferences: CloudPreferences) = throw CloudUnavailableException()
}

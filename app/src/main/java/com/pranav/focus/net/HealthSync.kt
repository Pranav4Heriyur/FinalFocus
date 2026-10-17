package com.pranav.focus.net

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pranav.focus.data.Activity
import com.pranav.focus.data.Polyline
import com.pranav.focus.data.Store
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.Earning
import kotlinx.coroutines.runBlocking
import java.time.Duration
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** One night of sleep, minutes per stage. */
data class Night(
    val startMs: Long, val endMs: Long, val deepMin: Int, val lightMin: Int, val remMin: Int,
    val awakeMin: Int, val wakings: Int, val asleepMin: Int,
)

/**
 * Reads runs, rides and workouts from Android Health Connect (your watch app and other fitness apps write there).
 * No web APIs, no keys: everything stays on the phone.
 */
object HealthSync {
    const val P_EXERCISE = "android.permission.health.READ_EXERCISE"
    const val P_ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"
    const val P_DISTANCE = "android.permission.health.READ_DISTANCE"
    const val P_HR = "android.permission.health.READ_HEART_RATE"
    const val P_KCAL = "android.permission.health.READ_ACTIVE_CALORIES_BURNED"
    const val P_ELEV = "android.permission.health.READ_ELEVATION_GAINED"
    const val P_SLEEP = "android.permission.health.READ_SLEEP"
    val PERMISSIONS = setOf(P_EXERCISE, P_ROUTES, P_DISTANCE, P_HR, P_KCAL, P_ELEV, P_SLEEP)

    fun sdkAvailable(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    fun needsInstall(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /** Cached result of the last permission check (updated by refreshPermissions / sync). */
    fun connected(ctx: Context) = Store.get(ctx).bool("hc_granted")

    fun refreshPermissions(ctx: Context): Boolean {
        if (!sdkAvailable(ctx)) { Store.get(ctx).putBool("hc_granted", false); return false }
        val ok = runCatching {
            runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }
        }.getOrDefault(emptySet()).let { P_EXERCISE in it }
        Store.get(ctx).putBool("hc_granted", ok)
        return ok
    }

    private fun typeOf(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Workout"
        else -> "Other"
    }

    /** Blocking; call off the main thread. */
    fun sync(ctx: Context) {
        runCatching {
            if (!sdkAvailable(ctx)) { NetState.health = "Health Connect isn’t available. Install or update it from Play Store."; return }
            if (!refreshPermissions(ctx)) { NetState.health = "Allow Health Connect access first."; return }
            NetState.health = "Syncing…"
            val client = HealthConnectClient.getOrCreate(ctx)
            val end = Instant.now()
            val start = end.minus(Duration.ofDays(45))
            val sessions = runBlocking {
                client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))).records
            }
            val zone = ZoneId.systemDefault()
            val out = ArrayList<Activity>()
            val srcs = HashMap<Long, MutableSet<String>>()
            for (r in sessions) {
                val type = typeOf(r.exerciseType)
                if (type == "Other") continue
                val range = TimeRangeFilter.between(r.startTime, r.endTime)
                var dist = 0.0
                var hr = 0.0
                var elev = 0.0
                var kcal = 0
                // each metric separately so one missing permission doesn't blank the others
                runCatching { dist = runBlocking { client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range)) }[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0 }
                runCatching { hr = (runBlocking { client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), range)) }[HeartRateRecord.BPM_AVG] ?: 0L).toDouble() }
                runCatching { elev = runBlocking { client.aggregate(AggregateRequest(setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL), range)) }[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0.0 }
                runCatching { kcal = (runBlocking { client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range)) }[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toInt() }
                Store.get(ctx).putInt("kcal_" + r.metadata.id.hashCode(), kcal)
                var poly = ""
                when (val rr = r.exerciseRouteResult) {
                    is ExerciseRouteResult.Data -> poly = Polyline.encode(rr.exerciseRoute.route.map { it.latitude to it.longitude })
                    is ExerciseRouteResult.ConsentRequired -> Store.get(ctx).putStr("hc_route_pending", r.metadata.id)
                    else -> {}
                }
                srcs.getOrPut(r.metadata.id.hashCode().toLong()) { sortedSetOf() }.add(srcName(r.metadata.dataOrigin.packageName))
                out.add(Activity(
                    r.metadata.id.hashCode().toLong(), type,
                    r.title?.takeIf { it.isNotBlank() } ?: when (type) { "Run" -> "Run"; "Ride" -> "Ride"; "Walk" -> "Walk"; else -> "Workout" },
                    r.startTime.toEpochMilli(), r.startTime.atZone(zone).toLocalDate().toString(),
                    dist, Duration.between(r.startTime, r.endTime).seconds.toInt(), hr, elev, poly,
                ))
            }
            // last night's sleep (minutes), for the Balance radar
            runCatching {
                val m = runBlocking { client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(end.minus(Duration.ofHours(30)), end))) }[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L
                Store.get(ctx).putInt("sleep_min", m.toInt())
            }
            // Sleep sessions with their stages (last 8 nights), for the Sleep card.
            runCatching {
                val sessions = runBlocking {
                    client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(end.minus(Duration.ofDays(8)), end))).records
                }
                val arr = JSONArray()
                for (r in sessions.sortedBy { it.endTime }) {
                    var deep = 0L; var light = 0L; var rem = 0L; var awake = 0L; var sleeping = 0L; var wakings = 0
                    for (st in r.stages) {
                        val m = Duration.between(st.startTime, st.endTime).toMinutes()
                        when (st.stage) {
                            SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
                            SleepSessionRecord.STAGE_TYPE_LIGHT -> light += m
                            SleepSessionRecord.STAGE_TYPE_REM -> rem += m
                            SleepSessionRecord.STAGE_TYPE_SLEEPING -> sleeping += m
                            SleepSessionRecord.STAGE_TYPE_AWAKE -> { awake += m; wakings++ }
                        }
                    }
                    val asleep = if (r.stages.isEmpty()) Duration.between(r.startTime, r.endTime).toMinutes() else deep + light + rem + sleeping
                    arr.put(JSONObject().put("s", r.startTime.toEpochMilli()).put("e", r.endTime.toEpochMilli())
                        .put("deep", deep).put("light", light).put("rem", rem).put("awake", awake)
                        .put("wakings", wakings).put("asleep", asleep))
                }
                Store.get(ctx).putStr("sleep_list", arr.toString())
            }
            // Club the watch (Samsung Health) and Strava: the same workout appears twice in Health Connect.
            val st = Store.get(ctx)
            val merged = ArrayList<Activity>()
            for (a in out.sortedBy { it.startMs }) {
                val i = merged.indexOfFirst { m -> m.type == a.type && overlaps(m, a) }
                if (i < 0) { merged.add(a); continue }
                val m = merged[i]
                val base = if (a.polyline.length > m.polyline.length) a else m
                st.putInt("kcal_" + base.id, maxOf(st.int("kcal_" + a.id), st.int("kcal_" + m.id)))
                srcs.getOrPut(base.id) { sortedSetOf() }.addAll(srcs[a.id].orEmpty() + srcs[m.id].orEmpty())
                merged[i] = base.copy(
                    distM = maxOf(a.distM, m.distM), movingS = maxOf(a.movingS, m.movingS),
                    avgHr = maxOf(a.avgHr, m.avgHr), elevM = maxOf(a.elevM, m.elevM),
                    polyline = base.polyline.ifEmpty { a.polyline.ifEmpty { m.polyline } },
                    name = if (m.name in setOf("Run", "Ride", "Workout")) a.name else m.name,
                )
            }
            merged.forEach { st.putStr("src_" + it.id, srcs[it.id].orEmpty().joinToString(" + ")) }
            val outFinal = merged
            Store.get(ctx).activities = outFinal.sortedByDescending { it.startMs }
            Earning.evaluate(ctx)
            Store.get(ctx).putLong("hc_last_sync", System.currentTimeMillis())
            NetState.health = "Synced ${outFinal.size} activities."
            AppState.dataTick++
        }.onFailure { NetState.health = "Couldn’t read Health Connect: ${it.javaClass.simpleName}" }
    }

    private fun srcName(pkg: String) = when {
        "shealth" in pkg || "samsung" in pkg -> "Samsung Health"
        else -> pkg.substringAfterLast('.')
    }

    private fun overlaps(a: Activity, b: Activity): Boolean {
        val aE = a.startMs + a.movingS * 1000L; val bE = b.startMs + b.movingS * 1000L
        val inter = minOf(aE, bE) - maxOf(a.startMs, b.startMs)
        val shorter = minOf(aE - a.startMs, bE - b.startMs).coerceAtLeast(1)
        return inter > shorter / 2
    }

    /** Stored sleep nights, oldest first. */
    fun nights(ctx: Context): List<Night> = runCatching {
        val arr = JSONArray(Store.get(ctx).str("sleep_list", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Night(o.getLong("s"), o.getLong("e"), o.getInt("deep"), o.getInt("light"), o.getInt("rem"),
                o.getInt("awake"), o.getInt("wakings"), o.getInt("asleep"))
        }
    }.getOrDefault(emptyList())

    fun syncAsync(ctx: Context) { Thread { sync(ctx) }.start() }

    /** Saves a route the user approved for one session (Android 14 per-session consent). */
    fun saveRoute(ctx: Context, route: ExerciseRoute?) {
        val id = Store.get(ctx).str("hc_route_pending")
        if (route == null || id.isEmpty()) return
        val poly = Polyline.encode(route.route.map { it.latitude to it.longitude })
        val key = id.hashCode().toLong()
        Store.get(ctx).activities = Store.get(ctx).activities.map { if (it.id == key) it.copy(polyline = poly) else it }
        Store.get(ctx).putStr("hc_route_pending", "")
        AppState.dataTick++
    }
}

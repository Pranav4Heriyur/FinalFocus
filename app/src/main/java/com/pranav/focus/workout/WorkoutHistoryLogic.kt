package com.pranav.focus.workout

/**
 * C12 calculations (pure Kotlin, unit-tested). History is built ONLY from persisted session + set_log rows. set_log stores the exercise
 * name at the time of logging, and workout_session stores the title, so later routine/exercise edits never rewrite an old session.
 * Volume / working-set rules are the same as C10 and C11: warm-ups are listed but never counted; volume = kg x reps for kg > 0 and reps > 0.
 */
data class HistoryItem(
    val id: String, val title: String, val activity: String, val startedAt: Long, val minutes: Long, val exercises: Int, val sets: Int,
    val volumeKg: Double, val distanceKm: Double, val exerciseNames: List<String>,
)
data class SetRow(val no: Int, val warmup: Boolean, val loadKg: Double?, val reps: Int?, val rpe: Int?, val durationS: Int?, val distanceM: Double?, val note: String)
data class ExerciseDetail(
    val exerciseId: String, val name: String, val muscle: String, val sets: List<SetRow>, val workingSets: Int, val volumeKg: Double,
    val totalReps: Int, val totalDurationS: Int, val totalDistanceM: Double,
)
data class SessionDetail(
    val id: String, val title: String, val subtitle: String, val activity: String, val routineId: String?, val startedAt: Long, val endedAt: Long?,
    val exercises: List<ExerciseDetail>, val workingSets: Int, val volumeKg: Double, val distanceKm: Double,
    val prs: List<CompletedPr>, val muscles: List<MuscleShare>, val unmappedSets: Int,
    val timing: SessionTiming = SessionTiming(null, null, null, null, false),
    /** true when a completion snapshot exists (names/muscles/category/PRs are as they were when the workout was saved). */
    val historical: Boolean = false,
) {
    /** True when the PR list was recomputed from saved earlier sets (legacy workout) instead of read from the completion snapshot. */
    val prsRecomputed: Boolean get() = !historical
    /** Elapsed minutes (the one 'duration' used by the history list). */
    val minutes: Long? get() = timing.elapsedMin
    val isGym get() = activity == "gym"
}

object WorkoutHistory {
    private fun vol(l: Double?, r: Int?) = if ((l ?: 0.0) > 0.0 && (r ?: 0) > 0) l!! * r!! else 0.0

    /** [sessions]: saved workouts. [workingSets]: their non-warm-up sets. */
    fun items(sessions: List<Session>, workingSets: List<SetLog>): List<HistoryItem> {
        val bySession = workingSets.groupBy { it.sessionId }
        return sessions.filter { it.status == "completed" && it.endedAt != null }.map { s ->
            val l = bySession[s.id].orEmpty(); val gym = s.activity == "gym"
            HistoryItem(
                s.id, s.title.ifBlank { "Workout" }, s.activity, s.startedAt, WorkoutTiming.of(s).elapsedMin ?: 0L,
                l.map { it.exerciseId }.distinct().size, if (gym) l.size else 0, if (gym) l.sumOf { vol(it.loadKg, it.reps) } else 0.0,
                l.sumOf { it.distanceM ?: 0.0 } / 1000.0, l.map { it.exerciseName }.distinct(),
            )
        }
    }

    /** Case-insensitive match on workout title or any logged exercise name; newest first unless [newestFirst] is false. */
    fun filter(items: List<HistoryItem>, query: String, newestFirst: Boolean = true): List<HistoryItem> {
        val q = query.trim().lowercase()
        val m = if (q.isEmpty()) items else items.filter { it.title.lowercase().contains(q) || it.exerciseNames.any { n -> n.lowercase().contains(q) } }
        return if (newestFirst) m.sortedByDescending { it.startedAt } else m.sortedBy { it.startedAt }
    }

    /**
     * [allSets] = every set of this session (warm-ups included, exercise ids already alias-resolved).
     * [prior] = working sets of saved sessions that STARTED BEFORE this one (a later session can never create or remove this session's PR).
     */
    fun detail(
        s: Session, allSets: List<SetLog>, prior: List<SetLog>, muscleMap: Map<String, Pair<String, String>>, subtitle: String,
        snap: List<SnapEx> = emptyList(), storedPrs: List<CompletedPr>? = null,
    ): SessionDetail {
        // Muscles: the completion snapshot wins; current exercise records are only a fallback for sessions saved before snapshots existed.
        val muscles = muscleMap + snap.associate { it.exerciseId to (it.primary to it.secondary) }
        val pos = snap.associate { it.exerciseId to it.position }
        val ordered = allSets.sortedWith(compareBy({ it.completedAt }, { it.id }))
        val exercises = ordered.groupBy { it.exerciseId }.map { (id, l) ->
            val work = l.filter { !it.warmup }
            ExerciseDetail(
                id, l.first().exerciseName, muscles[id]?.first.orEmpty(),
                l.map { SetRow(it.setNo, it.warmup, it.loadKg, it.reps, it.rpe, it.durationS, it.distanceM, it.note) },
                work.size, work.sumOf { vol(it.loadKg, it.reps) }, work.sumOf { it.reps ?: 0 }, work.sumOf { it.durationS ?: 0 }, work.sumOf { it.distanceM ?: 0.0 },
            )
        }.sortedBy { pos[it.exerciseId] ?: Int.MAX_VALUE }
        val gym = s.activity == "gym"
        val prs = if (!gym) emptyList() else storedPrs ?: ordered.filter { !it.warmup }.groupBy { it.exerciseId }.mapNotNull { (id, l) ->
            val best = l.filter { (it.loadKg ?: 0.0) > 0.0 }.maxWithOrNull(compareBy({ it.loadKg ?: 0.0 }, { it.reps ?: 0 })) ?: return@mapNotNull null
            val before = prior.filter { it.exerciseId == id && !it.warmup && (it.loadKg ?: 0.0) > 0.0 }.map { it.loadKg!! to it.reps }
            val r = detectPr(before, best.loadKg, best.reps) ?: return@mapNotNull null
            CompletedPr(id, best.exerciseName, muscles[id]?.first.orEmpty(), r.newKg, r.newReps, r.prevKg, r.prevReps)
        }.sortedByDescending { it.deltaKg }
        val credit = LinkedHashMap<String, Double>(); var unmapped = 0
        if (gym) exercises.forEach { e ->
            val (m, _) = WorkoutStats.mapMuscles(e.muscle)
            if (m.isEmpty()) unmapped += e.workingSets else m.forEach { credit[it] = (credit[it] ?: 0.0) + e.workingSets.toDouble() / m.size }
        }
        val tot = credit.values.sum()
        val shares = credit.entries.sortedByDescending { it.value }.map { MuscleShare(it.key, WorkoutStats.LABEL[it.key] ?: it.key, it.value, if (tot > 0) it.value / tot else 0.0) }
        return SessionDetail(
            s.id, s.title.ifBlank { "Workout" }, subtitle, s.activity, s.routineId, s.startedAt, s.endedAt, exercises,
            if (gym) exercises.sumOf { it.workingSets } else 0, if (gym) exercises.sumOf { it.volumeKg } else 0.0, exercises.sumOf { it.totalDistanceM } / 1000.0, prs, shares, unmapped,
            WorkoutTiming.of(s), s.snapV >= 1,
        )
    }

    /**
     * Loads one saved workout for C10 and C12 (single source, so both screens always agree). Sessions with a completion snapshot are rendered from it;
     * older sessions fall back to the stored sets only: no category (the routine may have been edited since), muscles from the current exercise
     * records, and PRs recomputed from saved earlier sets.
     */
    fun load(repo: WorkoutRepo, id: String?): SessionDetail {
        val s = repo.session(id ?: error("No workout selected.")) ?: error("This workout no longer exists.")
        check(s.status == "completed") { "This workout was not saved." }
        val all = repo.sessionSets(s.id).map { it.copy(exerciseId = repo.resolveExerciseId(it.exerciseId)) }
        if (s.snapV >= 1) {
            val sub = s.categoryLabel ?: s.activity.replaceFirstChar { it.uppercase() }
            return detail(s, all, emptyList(), emptyMap(), sub, repo.snapshotExercises(s.id), repo.storedPrs(s.id))   // snapshot only: no silent fallback to current definitions
        }
        val earlier = repo.statsSessions().filter { it.startedAt < s.startedAt && it.id != s.id }.map { it.id }.toSet()
        val prior = repo.statsSets().filter { it.sessionId in earlier }
        return detail(s, all, prior, repo.exerciseMuscles(), s.activity.replaceFirstChar { it.uppercase() })
    }

    fun shareText(d: SessionDetail, whenText: String): String = buildString {
        append(d.title).append(" — ").append(whenText).append('\n')
        append(listOfNotNull(d.minutes?.let { "$it min" }, "${d.exercises.size} exercises", if (d.isGym) "${d.workingSets} sets" else null, d.volumeKg.takeIf { it > 0 }?.let { "${Math.round(it)} kg volume" }).joinToString(" • ")).append('\n')
        d.exercises.forEach { e ->
            append("\n").append(e.name).append(": ")
            append(e.sets.filter { !it.warmup }.joinToString(", ") { r ->
                when { (r.loadKg ?: 0.0) > 0 && r.reps != null -> "${fmt(r.loadKg!!)}×${r.reps}"; r.reps != null -> "${r.reps} reps"; r.durationS != null -> "${r.durationS}s"; r.distanceM != null -> "${Math.round(r.distanceM)}m"; else -> "set" }
            })
        }
        if (d.prs.isNotEmpty()) append("\n\nPRs: ").append(d.prs.joinToString { "${it.name} ${fmt(it.newKg)} kg" })
        append("\n\nLogged with FOCUS")
    }
    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}

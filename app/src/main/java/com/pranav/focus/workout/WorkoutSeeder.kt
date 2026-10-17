package com.pranav.focus.workout

/**
 * Idempotent seeding. Safe to call on every launch:
 *  - the whole thing is skipped once meta.seed_version >= SEED_VERSION;
 *  - even when it runs, every insert is INSERT OR IGNORE / existence-guarded, so no duplicates and no overwrite of rows the user edited
 *    (routine lines are only written when the routine row itself is new; plan days are never rewritten).
 */
object WorkoutSeeder {
    fun seed(db: SqlRunner, now: Long = System.currentTimeMillis()) = db.tx {
        // meta.value is TEXT, so it arrives as a String: Row.int() would read it as 0 and the upgrade step below would never fire.
        val done = db.query("SELECT value FROM meta WHERE key = 'seed_version'").firstOrNull()?.str("value")?.trim()?.toIntOrNull() ?: 0
        if (done >= WorkoutCatalog.SEED_VERSION) return@tx

        for (e in WorkoutCatalog.EXERCISES) {
            db.exec(
                "INSERT OR IGNORE INTO exercise(id, name, equipment, primary_muscles, type, default_sets, default_reps, default_rest_s, unilateral, is_custom)" +
                    " VALUES(?,?,?,?,?,?,?,?,?,0)",
                e.id, e.name, e.equipment, e.primary, e.type, e.sets, e.reps, e.restS, if (e.unilateral) 1 else 0,
            )
        }
        for ((alias, target) in WorkoutCatalog.LEGACY_TO_CATALOG) db.exec("INSERT OR IGNORE INTO exercise_alias(alias, exercise_id, source) VALUES(?,?,'legacy_icon_id')", alias, target)
        for ((alias, target) in WorkoutCatalog.MANIFEST_TO_CATALOG) db.exec("INSERT OR IGNORE INTO exercise_alias(alias, exercise_id, source) VALUES(?,?,'asset_manifest')", alias, target)

        WorkoutCatalog.ROUTINES.forEachIndexed { idx, r ->
            if (db.count("SELECT COUNT(*) FROM routine WHERE id = ?", r.id) > 0) return@forEachIndexed
            db.exec(
                "INSERT INTO routine(id, code, name, category, focus, primary_focus, description, difficulty, minutes_low, minutes_high, label_hub, label_plans," +
                    " accent, kind, heavy_legs, notes, is_preset, sort) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?)",
                r.id, r.code, r.name, r.category, r.focus, r.primaryFocus, r.description, r.difficulty, r.minLow, r.minHigh, r.labelHub, r.labelPlans,
                r.accent, r.kind, if (r.heavyLegs) 1 else 0, r.notes, idx,
            )
            r.lines.forEachIndexed { pos, l ->
                val p = WorkoutLogic.parseReps(l.reps)
                db.exec(
                    "INSERT INTO routine_exercise(routine_id, exercise_id, position, sets, rep_text, rep_min, rep_max, rest_s, rest_text, unilateral, superset_group, note)" +
                        " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    r.id, l.exerciseId, pos, l.sets, l.reps, p.min, p.max, l.restS, l.restText, if (l.unilateral) 1 else 0, l.superset, l.note,
                )
            }
        }

        val t = WorkoutCatalog.TEMPLATES.first { it.id == "strength_conditioning_4day" }
        if (db.count("SELECT COUNT(*) FROM plan WHERE id = ?", WorkoutCatalog.PRESET_PLAN_ID) == 0) {
            val hasActive = db.count("SELECT COUNT(*) FROM plan WHERE is_active = 1") > 0
            db.exec(
                "INSERT INTO plan(id, name, description, plan_type, tags, template_id, is_active, is_preset, slot1_weekday, created_at, updated_at)" +
                    " VALUES(?,?,?,?,?,?,?,1,1,?,?)",
                WorkoutCatalog.PRESET_PLAN_ID, t.name, t.blurb, t.planType, "Strength,Hypertrophy,General Fitness,4-Day Split", t.id,
                if (hasActive) 0 else 1, now, now,
            )
            t.routineIds.forEachIndexed { i, rid -> db.exec("INSERT OR IGNORE INTO plan_routine(plan_id, routine_id, position) VALUES(?,?,?)", WorkoutCatalog.PRESET_PLAN_ID, rid, i) }
            t.days.forEachIndexed { i, d ->
                db.exec(
                    "INSERT OR IGNORE INTO plan_day(plan_id, slot, morning_routine_id, alt_morning_routine_id, gym_routine_id) VALUES(?,?,?,?,?)",
                    WorkoutCatalog.PRESET_PLAN_ID, i + 1, d.morning, d.altMorning, d.gym,
                )
            }
        }
        if (done in 1 until 2) fixDefaultScheduleV2(db)
        db.exec("INSERT OR REPLACE INTO meta(key, value) VALUES('seed_version', ?)", WorkoutCatalog.SEED_VERSION.toString())
    }

    /**
     * v1 shipped the preset plan with M3 (Easy Run) on day 3, the day before G3. Move it to M5 only when the row is still exactly what
     * v1 seeded (M3, no alternative, no gym, not customised, no notes); a day the user changed is theirs and is left alone.
     */
    private fun fixDefaultScheduleV2(db: SqlRunner) {
        db.exec(
            "UPDATE plan_day SET morning_routine_id = 'm5' WHERE plan_id = ? AND slot = 3 AND morning_routine_id = 'm3'" +
                " AND alt_morning_routine_id IS NULL AND gym_routine_id IS NULL AND customized = 0 AND notes = ''",
            WorkoutCatalog.PRESET_PLAN_ID,
        )
    }
}

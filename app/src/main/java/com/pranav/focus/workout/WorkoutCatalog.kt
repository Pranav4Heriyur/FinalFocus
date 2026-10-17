package com.pranav.focus.workout

/**
 * Seed data for workout.db. Source of truth: the supplied handoff (routines.json, exercise-catalog.json,
 * FOCUS_Workout_Claude_Engineering_Handoff.md section 4). Nothing here is invented: rest *ranges* ("2–3 min")
 * come from the handoff text, numeric rest seconds from routines.json.
 */
data class SeedEx(
    val id: String, val name: String, val equipment: String, val primary: String, val type: String,
    val sets: Int, val reps: String, val restS: Int, val unilateral: Boolean,
)

data class SeedLine(
    val exerciseId: String, val sets: Int, val reps: String, val restS: Int, val restText: String,
    val superset: String? = null, val unilateral: Boolean = false, val note: String = "",
)

data class SeedRoutine(
    val id: String, val code: String, val name: String, val category: String, val focus: String, val primaryFocus: String,
    val description: String, val difficulty: String, val minLow: Int, val minHigh: Int, val labelHub: String, val labelPlans: String,
    val accent: String, val kind: String, val heavyLegs: Boolean, val notes: String, val lines: List<SeedLine>,
)

data class SeedDay(val morning: String?, val altMorning: String?, val gym: String?)

data class SeedTemplate(
    val id: String, val name: String, val subtitle: String, val planType: String, val available: Boolean,
    val routineIds: List<String>, val days: List<SeedDay>, val blurb: String,
)

object WorkoutCatalog {
    const val SEED_VERSION = 3   // 3: added specific-muscle exercises (delts, forearms, arms) for C5; 2: default schedule no longer has a run before G3 (slot 3 is M5)
    const val PRESET_PLAN_ID = "plan_strength_conditioning_4day"

    val EXERCISES: List<SeedEx> = listOf(
    SeedEx("leg_press", "Leg Press", "Machine", "Quads", "reps", 4, "8-10", 150, false),
    SeedEx("hack_squat_or_smith_machine_squat", "Hack Squat (or Smith Machine Squat)", "Machine", "Quads", "reps", 3, "8-10", 120, false),
    SeedEx("dumbbell_romanian_deadlift", "Dumbbell Romanian Deadlift", "Dumbbells", "Hamstrings, Glutes", "reps", 3, "8-10", 120, false),
    SeedEx("lying_seated_leg_curl", "Lying / Seated Leg Curl", "Machine", "Hamstrings", "reps", 3, "10-12", 90, false),
    SeedEx("standing_seated_calf_raise", "Standing / Seated Calf Raise", "Machine", "Calves", "reps", 3, "12-15", 60, false),
    SeedEx("farmers_carry", "Farmer's Carry", "Dumbbells", "Full body", "carry", 3, "30-40 m", 90, false),
    SeedEx("weighted_assisted_pull_up_or_lat_pulldown", "Weighted / Assisted Pull-up or Lat Pulldown", "Machine / Bodyweight", "Back", "reps", 4, "5-8", 150, false),
    SeedEx("machine_chest_press_dumbbell_bench_press", "Machine Chest Press / Dumbbell Bench Press", "Machine / Dumbbells", "Chest", "reps", 4, "6-8", 150, false),
    SeedEx("seated_shoulder_press", "Seated Shoulder Press", "Machine / Dumbbells", "Shoulders", "reps", 3, "8-10", 120, false),
    SeedEx("seated_cable_chest_supported_row", "Seated Cable / Chest-supported Row", "Cable / Machine", "Back", "reps", 3, "8-10", 90, false),
    SeedEx("lateral_raise", "Lateral Raise", "Dumbbells / Cable", "Shoulders", "reps", 3, "12-15", 0, false),
    SeedEx("triceps_rope_pushdown", "Triceps Rope Pushdown", "Cable", "Triceps", "reps", 3, "10-12", 75, false),
    SeedEx("dumbbell_cable_curl", "Dumbbell / Cable Curl", "Dumbbells / Cable", "Biceps", "reps", 2, "10-12", 60, false),
    SeedEx("hip_thrust", "Hip Thrust", "Machine / Barbell", "Glutes", "reps", 4, "8-10", 120, false),
    SeedEx("walking_lunge_bulgarian_split_squat", "Walking Lunge / Bulgarian Split Squat", "Dumbbells / Bodyweight", "Quads, Glutes", "reps", 3, "8-10 per leg", 90, true),
    SeedEx("leg_extension", "Leg Extension", "Machine", "Quads", "reps", 3, "12-15", 75, false),
    SeedEx("45_degree_back_extension", "45-degree Back Extension", "Bench", "Back, Glutes", "reps", 3, "10-12", 90, false),
    SeedEx("sled_push_sandbag_carry", "Sled Push / Sandbag Carry", "Sled / Sandbag", "Full body", "carry", 4, "20-30 m", 90, false),
    SeedEx("suitcase_carry", "Suitcase Carry", "Dumbbell / Kettlebell", "Core", "carry", 3, "30 m per side", 60, true),
    SeedEx("pallof_press", "Pallof Press", "Cable / Band", "Core", "reps", 3, "10 per side", 60, true),
    SeedEx("incline_dumbbell_press", "Incline Dumbbell Press", "Dumbbells", "Chest", "reps", 4, "8-10", 120, false),
    SeedEx("neutral_pulldown_strict_pull_up", "Neutral Pulldown / Strict Pull-up", "Cable / Bodyweight", "Back", "reps", 3, "8-12", 120, false),
    SeedEx("one_arm_dumbbell_cable_row", "One-arm Dumbbell / Cable Row", "Dumbbells / Cable", "Back", "reps", 3, "8-12 per arm", 90, true),
    SeedEx("cable_fly_pec_deck", "Cable Fly / Pec Deck", "Cable / Machine", "Chest", "reps", 3, "12-15", 0, false),
    SeedEx("face_pull", "Face Pull", "Cable", "Rear delts", "reps", 3, "15", 75, false),
    SeedEx("overhead_cable_triceps_extension", "Overhead Cable Triceps Extension", "Cable", "Triceps", "reps", 2, "10-12", 0, false),
    SeedEx("hammer_curl", "Hammer Curl", "Dumbbells", "Biceps", "reps", 2, "10-12", 60, false),
    SeedEx("dead_hang", "Dead Hang", "Pull-up bar", "Grip", "hold", 2, "Max time", 60, false),
    // ---- morning-routine movements (names/prescriptions from the handoff, section 4) ----
    SeedEx("dead_bug", "Dead Bug", "Bodyweight", "Core", "reps", 3, "10", 45, false),
    SeedEx("plank", "Plank", "Bodyweight", "Core", "hold", 3, "30-45 sec", 45, false),
    SeedEx("bird_dog", "Bird Dog", "Bodyweight", "Core, Back", "reps", 3, "10 per side", 45, true),
    SeedEx("hollow_body_hold", "Hollow Body Hold", "Bodyweight", "Core", "hold", 3, "20-30 sec", 45, false),
    SeedEx("glute_bridge", "Glute Bridge", "Bodyweight", "Glutes", "reps", 3, "15", 45, false),
    SeedEx("hip_90_90", "Hip 90/90", "Bodyweight", "Hips", "mobility", 1, "Mobility block", 0, false),
    SeedEx("thoracic_rotations", "Thoracic Rotations", "Bodyweight", "Back", "mobility", 1, "Mobility block", 0, false),
    SeedEx("deep_squat_hold", "Deep Squat Hold", "Bodyweight", "Hips", "mobility", 1, "Mobility block", 0, false),
    SeedEx("band_shoulder_dislocates", "Band Shoulder Dislocates", "Band", "Shoulders", "mobility", 1, "Mobility block", 0, false),
    SeedEx("cat_cow", "Cat-Cow", "Bodyweight", "Back", "mobility", 1, "Mobility block", 0, false),
    SeedEx("jump_rope", "Jump Rope", "Jump rope", "Cardio", "interval", 1, "30 sec on / 30 sec off", 0, false),
    SeedEx("shadow_boxing", "Shadow Boxing", "Bodyweight", "Cardio", "interval", 1, "30 sec on / 30 sec off", 0, false),
    SeedEx("easy_run", "Easy Run", "None", "Cardio", "run", 1, "2-4 km", 0, false),
    SeedEx("ruck_walk", "Ruck Walk", "Backpack", "Full body", "ruck", 1, "30-40 min", 0, false),
    SeedEx("recovery_mobility", "Recovery Mobility", "Bodyweight", "Full body", "mobility", 1, "10-15 min", 0, false),
    // ---- C5 additions: specific-muscle exercises so every arm / delt / forearm filter has catalogue entries ----
    SeedEx("preacher_curl", "Preacher Curl", "Machine / Dumbbells", "Biceps", "reps", 3, "10-12", 60, false),
    SeedEx("incline_dumbbell_curl", "Incline Dumbbell Curl", "Dumbbells", "Biceps", "reps", 3, "10-12", 60, false),
    SeedEx("skull_crusher", "Skull Crusher", "Barbell / Dumbbells", "Triceps", "reps", 3, "8-12", 75, false),
    SeedEx("close_grip_push_up", "Close-grip Push-up", "Bodyweight", "Triceps, Chest", "reps", 3, "8-15", 60, false),
    SeedEx("front_raise", "Front Raise", "Dumbbells / Cable", "Front delts", "reps", 3, "10-12", 60, false),
    SeedEx("dumbbell_shoulder_press", "Dumbbell Shoulder Press", "Dumbbells", "Front delts, Triceps", "reps", 3, "8-10", 90, false),
    SeedEx("cable_lateral_raise", "Cable Lateral Raise", "Cable", "Side delts", "reps", 3, "12-15", 60, true),
    SeedEx("upright_row", "Upright Row", "Barbell / Cable", "Side delts", "reps", 3, "10-12", 75, false),
    SeedEx("reverse_pec_deck", "Reverse Pec Deck", "Machine", "Rear delts", "reps", 3, "12-15", 60, false),
    SeedEx("bent_over_rear_delt_fly", "Bent-over Rear Delt Fly", "Dumbbells", "Rear delts", "reps", 3, "12-15", 60, false),
    SeedEx("wrist_curl", "Wrist Curl", "Dumbbells / Barbell", "Forearms", "reps", 3, "12-20", 45, false),
    SeedEx("reverse_curl", "Reverse Curl", "Barbell / Cable", "Forearms, Biceps", "reps", 3, "10-15", 60, false),
    )

    /** Legacy WorkoutAssets ids (the pictogram registry shipped before this module) -> catalogue ids. Exactly one per exercise. */
    val LEGACY_TO_CATALOG: Map<String, String> = linkedMapOf(
        "g1-leg-press" to "leg_press",
        "g1-hack-squat-smith-squat" to "hack_squat_or_smith_machine_squat",
        "g1-dumbbell-romanian-deadlift" to "dumbbell_romanian_deadlift",
        "g1-lying-seated-leg-curl" to "lying_seated_leg_curl",
        "g1-standing-seated-calf-raise" to "standing_seated_calf_raise",
        "g1-farmers-carry" to "farmers_carry",
        "g2-weighted-assisted-pullup-lat-pulldown" to "weighted_assisted_pull_up_or_lat_pulldown",
        "g2-machine-chest-press-dumbbell-bench-press" to "machine_chest_press_dumbbell_bench_press",
        "g2-seated-shoulder-press" to "seated_shoulder_press",
        "g2-seated-cable-chest-supported-row" to "seated_cable_chest_supported_row",
        "g2-lateral-raise" to "lateral_raise",
        "g2-triceps-rope-pushdown" to "triceps_rope_pushdown",
        "g2-dumbbell-cable-curl" to "dumbbell_cable_curl",
        "g3-hip-thrust" to "hip_thrust",
        "g3-walking-lunge-bulgarian-split-squat" to "walking_lunge_bulgarian_split_squat",
        "g3-leg-extension" to "leg_extension",
        "g3-45-degree-back-extension" to "45_degree_back_extension",
        "g3-sled-push-sandbag-carry" to "sled_push_sandbag_carry",
        "g3-suitcase-carry" to "suitcase_carry",
        "g3-pallof-press" to "pallof_press",
        "g4-incline-dumbbell-press" to "incline_dumbbell_press",
        "g4-neutral-grip-pulldown-strict-pullup" to "neutral_pulldown_strict_pull_up",
        "g4-one-arm-dumbbell-cable-row" to "one_arm_dumbbell_cable_row",
        "g4-cable-fly-pec-deck" to "cable_fly_pec_deck",
        "g4-face-pulls" to "face_pull",
        "g4-overhead-cable-triceps-extension" to "overhead_cable_triceps_extension",
        "g4-hammer-curl" to "hammer_curl",
        "g4-dead-hang" to "dead_hang",
        "m1-dead-bug" to "dead_bug",
        "m1-plank" to "plank",
        "m1-bird-dog" to "bird_dog",
        "m1-hollow-body-hold" to "hollow_body_hold",
        "m1-glute-bridge" to "glute_bridge",
        "m2-hip-90-90" to "hip_90_90",
        "m2-thoracic-rotations" to "thoracic_rotations",
        "m2-deep-squat-hold" to "deep_squat_hold",
        "m2-band-shoulder-dislocates" to "band_shoulder_dislocates",
        "m2-cat-cow" to "cat_cow",
        "m2-jump-rope" to "jump_rope",
        "m2-shadow-boxing" to "shadow_boxing",
        "m3-easy-run" to "easy_run",
        "m4-ruck-walk" to "ruck_walk",
        "m5-recovery-mobility" to "recovery_mobility",
    )

    /** Ids used by the asset pack's exercise_asset_manifest.json -> catalogue ids (only where they differ). */
    val MANIFEST_TO_CATALOG: Map<String, String> = linkedMapOf(
        "lying_or_seated_leg_curl" to "lying_seated_leg_curl",
        "standing_or_seated_calf_raise" to "standing_seated_calf_raise",
        "farmer_s_carry" to "farmers_carry",
        "weighted_pull_up_assisted_pull_up_lat_pulldown" to "weighted_assisted_pull_up_or_lat_pulldown",
        "machine_chest_press_or_dumbbell_bench_press" to "machine_chest_press_dumbbell_bench_press",
        "seated_cable_row_or_chest_supported_row" to "seated_cable_chest_supported_row",
        "dumbbell_or_cable_curl" to "dumbbell_cable_curl",
        "walking_lunge_or_bulgarian_split_squat" to "walking_lunge_bulgarian_split_squat",
        "sled_push_or_sandbag_carry" to "sled_push_sandbag_carry",
        "neutral_grip_pulldown_or_strict_pull_up" to "neutral_pulldown_strict_pull_up",
        "one_arm_dumbbell_or_cable_row" to "one_arm_dumbbell_cable_row",
        "cable_fly_or_pec_deck" to "cable_fly_pec_deck",
        "face_pulls" to "face_pull",
    )

    /**
     * Catalogue id -> raster illustration key (see WorkoutImages). These are *generated illustrations* cropped from a
     * contact sheet, NOT photographs. Only same-movement matches are used. The asset manifest also proposed
     * back_squat for the hack squat, barbell_bench_press for chest press / DB bench, and pull_up for dead hang;
     * those are different exercises, so they are deliberately left to the pictogram fallback.
     */
    val ILLUSTRATION_KEY: Map<String, String> = mapOf(
        "leg_press" to "leg_press",
        "dumbbell_romanian_deadlift" to "dumbbell_romanian_deadlift",
        "lying_seated_leg_curl" to "lying_leg_curl",
        "weighted_assisted_pull_up_or_lat_pulldown" to "pull_up",
        "neutral_pulldown_strict_pull_up" to "pull_up",
        "seated_shoulder_press" to "seated_shoulder_press",
        "lateral_raise" to "dumbbell_lateral_raise",
        "incline_dumbbell_press" to "incline_dumbbell_press",
        "plank" to "plank",
        "easy_run" to "easy_run",
        "ruck_walk" to "ruck_walk",
        "recovery_mobility" to "recovery_mobility",
    )

    /** Routine cover art key. Gym covers reuse the routine's first matching exercise illustration. M2 has none. */
    val ROUTINE_COVER_KEY: Map<String, String> = mapOf(
        "g1" to "leg_press", "g2" to "pull_up", "g3" to "lying_leg_curl", "g4" to "incline_dumbbell_press",
        "m1" to "plank", "m3" to "easy_run", "m4" to "ruck_walk", "m5" to "recovery_mobility",
    )

    private fun l(ex: String, sets: Int, reps: String, rest: Int, text: String, ss: String? = null, uni: Boolean = false, note: String = "") =
        SeedLine(ex, sets, reps, rest, text, ss, uni, note)

    private const val SS = "No rest · superset"

    val ROUTINES: List<SeedRoutine> = listOf(
        SeedRoutine("g1", "G1", "Lower Body A", "gym", "Quads", "Quads",
            "A quad-dominant lower body session with compound lifts, accessories and carries. Build strength and size while improving overall lower body stability.",
            "Intermediate", 50, 70, "~ 60 min", "~ 50–70 min", "red", "strength", true, "",
            listOf(
                l("leg_press", 4, "8-10", 150, "2–3 min"),
                l("hack_squat_or_smith_machine_squat", 3, "8-10", 120, "2 min"),
                l("dumbbell_romanian_deadlift", 3, "8-10", 120, "2 min"),
                l("lying_seated_leg_curl", 3, "10-12", 90, "90 sec"),
                l("standing_seated_calf_raise", 3, "12-15", 60, "60 sec"),
                l("farmers_carry", 3, "30-40 m", 90, "90 sec"),
            )),
        SeedRoutine("g2", "G2", "Upper Body A", "gym", "Strength", "Back & Chest",
            "An upper body strength session built on heavy pulling and pressing, finished with a shoulder/triceps superset and arm work.",
            "Intermediate", 55, 75, "~ 60 min", "~ 55–75 min", "blue", "strength", false, "",
            listOf(
                l("weighted_assisted_pull_up_or_lat_pulldown", 4, "5-8", 150, "2–3 min"),
                l("machine_chest_press_dumbbell_bench_press", 4, "6-8", 150, "2–3 min"),
                l("seated_shoulder_press", 3, "8-10", 120, "2 min"),
                l("seated_cable_chest_supported_row", 3, "8-10", 90, "90 sec"),
                l("lateral_raise", 3, "12-15", 0, SS, ss = "A"),
                l("triceps_rope_pushdown", 3, "10-12", 75, "60–90 sec after pair", ss = "A"),
                l("dumbbell_cable_curl", 2, "10-12", 60, "60 sec"),
            )),
        SeedRoutine("g3", "G3", "Lower Body B", "gym", "Hamstrings • Glutes", "Hamstrings & Glutes",
            "A posterior-chain lower body session: hip thrusts, single-leg work, extensions and loaded carries.",
            "Intermediate", 55, 75, "~ 60 min", "~ 55–75 min", "red", "strength", true, "",
            listOf(
                l("hip_thrust", 4, "8-10", 120, "2 min"),
                l("walking_lunge_bulgarian_split_squat", 3, "8-10 per leg", 90, "90 sec", uni = true),
                l("leg_extension", 3, "12-15", 75, "60–90 sec"),
                l("45_degree_back_extension", 3, "10-12", 90, "90 sec", note = "Add load when easy."),
                l("sled_push_sandbag_carry", 4, "20-30 m", 90, "90 sec"),
                l("suitcase_carry", 3, "30 m per side", 60, "60 sec", uni = true),
                l("pallof_press", 3, "10 per side", 60, "60 sec", uni = true),
            )),
        SeedRoutine("g4", "G4", "Upper Body B", "gym", "Physique • Grip", "Chest & Back",
            "A physique and grip focused upper body session with two superset pairs and a finishing dead hang.",
            "Intermediate", 50, 70, "~ 60 min", "~ 50–70 min", "blue", "strength", false, "",
            listOf(
                l("incline_dumbbell_press", 4, "8-10", 120, "2 min"),
                l("neutral_pulldown_strict_pull_up", 3, "8-12", 120, "2 min"),
                l("one_arm_dumbbell_cable_row", 3, "8-12 per arm", 90, "90 sec", uni = true),
                l("cable_fly_pec_deck", 3, "12-15", 0, SS, ss = "B"),
                l("face_pull", 3, "15", 75, "60–90 sec after pair", ss = "B"),
                l("overhead_cable_triceps_extension", 2, "10-12", 0, SS, ss = "C"),
                l("hammer_curl", 2, "10-12", 60, "60 sec after pair", ss = "C"),
                l("dead_hang", 2, "Max time", 60, "60 sec"),
            )),
        SeedRoutine("m1", "M1", "Core", "morning", "Core strength and stability", "Core",
            "Core strength and stability.", "Beginner", 15, 20, "~ 15 min", "~ 15–20 min", "grey", "circuit", false,
            "Three rounds, 30–60 sec rest between rounds. Progress time/reps, then harder variations (long-lever plank, ab wheel).",
            listOf(
                l("dead_bug", 3, "10", 0, "Circuit"),
                l("plank", 3, "30-45 sec", 0, "Circuit"),
                l("bird_dog", 3, "10 per side", 0, "Circuit", uni = true),
                l("hollow_body_hold", 3, "20-30 sec", 0, "Circuit"),
                l("glute_bridge", 3, "15", 0, "Circuit"),
            )),
        SeedRoutine("m2", "M2", "Mobility + Conditioning", "morning", "Mobility flow + jump rope/boxing", "Mobility",
            "Mobility flow + jump rope/boxing.", "Beginner", 16, 18, "~ 16 min", "~ 16–18 min", "grey", "mobility", false,
            "Part 1 (8 min): mobility. Part 2 (8–10 min): jump rope or shadow boxing, 30 sec on / 30 sec off, easy-to-moderate pace.",
            listOf(
                l("hip_90_90", 1, "Part 1 · 8 min block", 0, "Flow"),
                l("thoracic_rotations", 1, "Part 1 · 8 min block", 0, "Flow"),
                l("deep_squat_hold", 1, "Part 1 · 8 min block", 0, "Flow"),
                l("band_shoulder_dislocates", 1, "Part 1 · 8 min block", 0, "Flow"),
                l("cat_cow", 1, "Part 1 · 8 min block", 0, "Flow"),
                l("jump_rope", 1, "30 sec on / 30 sec off", 0, "Interval", note = "Part 2 · 8–10 min. Jump rope or shadow boxing."),
                l("shadow_boxing", 1, "30 sec on / 30 sec off", 0, "Interval", note = "Part 2 alternative."),
            )),
        SeedRoutine("m3", "M3", "Easy Run", "morning", "Conversational pace running", "Cardio",
            "Conversational pace running.", "Beginner", 20, 35, "2–4 km", "2–4 km", "green", "run", false,
            "Weeks 1–2: 2 km; weeks 3–4: 3 km; week 5 onward: 4 km. Add no more than ~0.5 km/week. Optional: 4 × 20 sec pickups mid-run. Track distance, time and effort (1–10).",
            listOf(l("easy_run", 1, "2-4 km", 0, "")),
        ),
        SeedRoutine("m4", "M4", "Ruck Walk", "morning", "5–10 kg backpack brisk walk", "Cardio",
            "5–10 kg backpack brisk walk.", "Beginner", 30, 40, "30–40 min", "30–40 min", "green", "ruck", false,
            "May replace one run per week. Track pack weight, duration and distance.",
            listOf(l("ruck_walk", 1, "30-40 min", 0, ""))),
        SeedRoutine("m5", "M5", "Recovery Mobility", "morning", "Easy mobility and stretching", "Mobility",
            "Easy mobility and stretching.", "Beginner", 10, 15, "~ 10 min", "~ 10–15 min", "purple", "mobility", false, "",
            listOf(l("recovery_mobility", 1, "10-15 min", 0, ""))),
    )

    /**
     * Handoff "Editable weekly schedule" table, amended: the supplied day 3 (M3 Easy Run) sat the day before G3, which breaks the
     * no run/ruck before G1/G3 rule, so day 3 is M5 Recovery Mobility. Slot 6 (M3 or M4) is the week's run/ruck and is safe:
     * day 7 is M5 and day 1 is not preceded by one.
     */
    val DEFAULT_DAYS: List<SeedDay> = listOf(
        SeedDay("m1", null, "g1"), SeedDay("m2", null, "g2"), SeedDay("m5", null, null), SeedDay("m1", null, "g3"),
        SeedDay("m2", null, "g4"), SeedDay("m3", "m4", null), SeedDay("m5", null, null),
    )

    val TEMPLATES: List<SeedTemplate> = listOf(
        SeedTemplate("strength_conditioning_4day", "Strength & Conditioning — 4-Day Split", "4 gym + 5 morning", "hybrid", true,
            listOf("g1", "g2", "g3", "g4", "m1", "m2", "m3", "m4", "m5"), DEFAULT_DAYS,
            "4 gym routines, 5 morning routines and a 7-day schedule. Built for strength, hypertrophy and overall fitness."),
        // The handoff supplies no routines for these templates, so they are listed but not selectable (no invented content).
        SeedTemplate("upper_lower", "Upper / Lower", "4-day split", "gym", false, emptyList(), emptyList(), ""),
        SeedTemplate("push_pull_legs", "Push Pull Legs", "6-day split", "gym", false, emptyList(), emptyList(), ""),
        SeedTemplate("full_body", "Full Body", "3-day split", "gym", false, emptyList(), emptyList(), ""),
    )

    val PROGRAM_RULES: List<Triple<String, String, String>> = listOf(
        Triple("Warm-up", "5 min light cardio + 1–2 ramp-up sets", "flame"),
        Triple("Effort", "1–3 reps in reserve (RIR). Weeks 1–2: 3–4 RIR", "chart"),
        Triple("Rest", "2–3 min compounds, 60–90 sec others, 0 sec between supersets", "clock"),
        Triple("Progression", "Double progression + deload option (every 6 weeks)", "chart"),
    )
}

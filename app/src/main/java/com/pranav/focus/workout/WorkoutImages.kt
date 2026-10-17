package com.pranav.focus.workout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico

/**
 * Media lookup. Three honest tiers, never mixed up:
 *  1. ILLUSTRATION - generated monochrome illustration (NOT a photograph), only where the exercise really matches;
 *  2. PICTOGRAM    - the vector exercise pictogram from the asset pack;
 *  3. GENERIC      - dumbbell glyph (custom exercises / no asset).
 */
object WorkoutImages {
    private val FULL: Map<String, Int> = mapOf(
        "leg_press" to R.drawable.wkimg_leg_press,
        "dumbbell_romanian_deadlift" to R.drawable.wkimg_dumbbell_romanian_deadlift,
        "lying_leg_curl" to R.drawable.wkimg_lying_leg_curl,
        "pull_up" to R.drawable.wkimg_pull_up,
        "seated_shoulder_press" to R.drawable.wkimg_seated_shoulder_press,
        "dumbbell_lateral_raise" to R.drawable.wkimg_dumbbell_lateral_raise,
        "incline_dumbbell_press" to R.drawable.wkimg_incline_dumbbell_press,
        "plank" to R.drawable.wkimg_plank,
        "easy_run" to R.drawable.wkimg_easy_run,
        "ruck_walk" to R.drawable.wkimg_ruck_walk,
        "recovery_mobility" to R.drawable.wkimg_recovery_mobility,
    )
    private val THUMB: Map<String, Int> = mapOf(
        "leg_press" to R.drawable.wkimg_leg_press_thumb,
        "dumbbell_romanian_deadlift" to R.drawable.wkimg_dumbbell_romanian_deadlift_thumb,
        "lying_leg_curl" to R.drawable.wkimg_lying_leg_curl_thumb,
        "pull_up" to R.drawable.wkimg_pull_up_thumb,
        "seated_shoulder_press" to R.drawable.wkimg_seated_shoulder_press_thumb,
        "dumbbell_lateral_raise" to R.drawable.wkimg_dumbbell_lateral_raise_thumb,
        "incline_dumbbell_press" to R.drawable.wkimg_incline_dumbbell_press_thumb,
        "plank" to R.drawable.wkimg_plank_thumb,
        "easy_run" to R.drawable.wkimg_easy_run_thumb,
        "ruck_walk" to R.drawable.wkimg_ruck_walk_thumb,
        "recovery_mobility" to R.drawable.wkimg_recovery_mobility_thumb,
    )
    private val CATALOG_TO_LEGACY: Map<String, String> = WorkoutCatalog.LEGACY_TO_CATALOG.entries.associate { (k, v) -> v to k }

    @DrawableRes fun exerciseIllustration(exerciseId: String, thumb: Boolean): Int? =
        WorkoutCatalog.ILLUSTRATION_KEY[exerciseId]?.let { (if (thumb) THUMB else FULL)[it] }

    @DrawableRes fun routineCover(routineId: String): Int? = WorkoutCatalog.ROUTINE_COVER_KEY[routineId]?.let { FULL[it] }

    @DrawableRes fun pictogram(exerciseId: String): Int =
        CATALOG_TO_LEGACY[exerciseId]?.let { WorkoutAssets.exercise(it)?.icon } ?: WorkoutAssets.ui("dumbbell")

    /** True when [exerciseId] has a real exercise-matched illustration (used to label the media honestly). */
    fun hasIllustration(exerciseId: String) = exerciseIllustration(exerciseId, false) != null
}

/** Square exercise thumbnail: illustration when one matches, else the vector pictogram on a raised tile. */
@Composable
fun ExerciseThumb(exerciseId: String, size: Dp = 48.dp, radius: Dp = 12.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(radius)
    val img = WorkoutImages.exerciseIllustration(exerciseId, thumb = true)
    Box(modifier.size(size).clip(shape).background(C.Raised), contentAlignment = Alignment.Center) {
        if (img != null) Image(painterResource(img), "Exercise illustration", Modifier.size(size), contentScale = ContentScale.Crop)
        else Ico(WorkoutImages.pictogram(exerciseId), size * 0.58f, C.White.copy(alpha = 0.85f), Modifier.semantics { contentDescription = "Exercise pictogram" })
    }
}

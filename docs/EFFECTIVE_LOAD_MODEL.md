# Effective Load Model v1: one formula for any workout, including exercises the app has never seen

Supersedes section 4 of `FOCUS_APP_WORKOUT_SPEC.md`. Reference code: `effective_load.py` (tested, port to Kotlin).

## 0. What this can and can't claim

- No peer-reviewed formula gives the "effectiveness" of a workout. Real effectiveness is adaptation, and that is individual.
- What the model estimates is the **training stimulus**: how hard and how close to failure each muscle was worked, plus the whole-body load. It then checks your personal progress against that stimulus.
- Each component is tagged **[Published]** (taken from literature) or **[Heuristic]** (engineering choice, tunable in settings). Don't present the heuristic parts as science in the UI.
- Before the references go in any public text, verify them. I'm citing from memory.

## 1. The pipeline (works with the minimum input: duration + effort)

```
Any workout
 ├─ Lifting set (known or unknown exercise) ──► sections 2-4 ──► muscle stimulus (HSE) + strength (e1RM)
 ├─ Cardio / sport / anything else ───────────► section 5     ──► load units (LU)
 └─ All of it ────────────────────────────────► section 6     ──► Fitness / Fatigue / Form
```

## 2. Per set (resistance)

**Inputs:** external kg, reps, optional RPE, set type (warm-up sets are skipped entirely).

1. **RIR** [Published: Zourdos 2016; Helms 2016]. `RIR = 10 - RPE`. Failure set = 0. If RPE is blank, assume RIR 2 [Heuristic, flagged low-confidence; lifters tend to stop 1-3 reps short of failure, Halperin 2022].
2. **Effective load** (bodyweight, assisted, weighted). `load = external + bodyweight x factor - assistance`.
   Factors [Heuristic, approximate from kinetic studies, e.g. Ebben 2011 for push-ups]: pull-up/chin-up 1.0, dip 0.95, push-up 0.65, inverted row 0.5, plank-type 0 (use duration).
3. **Reps to failure** `RTF = reps + RIR`.
4. **e1RM** [Published: Epley 1985; Brzycki 1993]. Average of both:
   - `Epley = load x (1 + RTF/30)`
   - `Brzycki = load x 36 / (37 - RTF)`
   - `RTF = 1 -> e1RM = load`. `RTF > 15 -> not computed` (formulas are unreliable above ~10-15 reps, so these sets still count for stimulus but not for e1RM).
   - Averaging removes the jump you'd get by switching formulas at a rep count.
5. **Relative intensity** `I = load / e1RM_ref`.
   `e1RM_ref` = your best e1RM for that exercise in the last 8 weeks (personal reference). With no history it falls back to this set's own e1RM, then to an assumed 0.45 for high-rep sets. Each fallback lowers the confidence label.

## 3. Muscle stimulus: Hard-Set Equivalents (HSE)

```
effective_reps = max(0, min(reps, 5 - RIR))          # reps within ~5 of failure [Heuristic]
g(I)           = min(1, I / 0.30)                    # below ~30% 1RM, stimulus fades [Published: Schoenfeld 2017; Lopez 2021]
HSE(set)       = effective_reps / 5 x g(I)           # 1.0 = a hard set taken to failure at a decent load
muscle_HSE(m)  = sum over sets of HSE(set) x credit(m, exercise)
```

- **Why this shape:** proximity to failure and weekly set volume are the best-supported drivers of hypertrophy (Schoenfeld, Ogborn & Krieger 2017; Refalo 2023; Robinson 2024), and loads from ~30% 1RM up work if sets are taken close to failure (Lopez 2021). Low-rep sets get less credit per set, which matches the data that heavy singles are not hypertrophy-efficient.
- **Weekly target:** 10-20 HSE per muscle group per week as the default band [Published range: Schoenfeld 2017; Pelland 2024 preprint, with wide uncertainty]. User-adjustable.
- **Worked values:** 8 reps @ RPE 8 = 0.60. 5 reps to failure = 1.00. 10 reps @ RPE 9 = 0.80.

## 4. Unknown exercises: how the app handles something not in the library

The goal is that nothing is ever "unsupported".

1. **Look up** the name (fuzzy match). If found, use the stored type, muscles and bodyweight factor.
2. **Smart classify** (3 taps, 10 seconds). Offline keyword rules pre-fill suggestions ("incline", "curl", "row", "squat"...). The user confirms:
   - **Type:** weight x reps / bodyweight / weighted bodyweight / assisted / duration / distance.
   - **Movement pattern:** horizontal push, vertical push, vertical pull, horizontal pull, squat, hinge, lunge, core, carry, or "isolation".
   - **Primary muscle** (only needed for isolation).
3. **Muscle credit** comes from the pattern template [Heuristic; EMG does not map one-to-one to growth, and fractional counting of secondary muscles is a practical convention, not a settled result]:

| Pattern | Credit |
|---|---|
| Horizontal push | chest 1, triceps 0.5, front delts 0.5 |
| Vertical push | front delts 1, triceps 0.5, chest 0.5 |
| Vertical pull | lats 1, biceps 0.5, upper back 0.5 |
| Horizontal pull | upper back 1, lats 0.5, biceps 0.5, rear delts 0.5 |
| Squat / lunge | quads 1, glutes 0.5 |
| Hinge | glutes 1, hamstrings 1, lower back 0.5 |
| Core | core 1 |
| Carry | core 1, forearms 0.5, traps 0.5 |
| Isolation | chosen muscle 1 |

4. **Self-calibration.** An unknown exercise needs no external norm. After ~3 sessions it has its own `e1RM_ref`, so `I` becomes real and the confidence label goes low -> medium -> high. Cross-exercise comparison never uses raw kg. It uses HSE and load units, which are effort-based.
5. **Not a lift at all?** (sport, swim, yoga, climbing...) go to section 5. No muscle credit unless the user picks a template.

**Confidence label** shown next to each exercise: Low (assumed RIR and no history), Medium (own-set e1RM), High (RPE logged and personal reference exists).

## 5. Any other activity: Load Units (LU)

`LU` = sRPE-minutes, the common currency for everything.

Priority order, using whatever data exists:
1. **sRPE** [Published: Foster et al. 2001]. `LU = session RPE (1-10) x minutes`. This is the minimum input (duration + one slider).
2. **Heart rate** (Strava / Health Connect). Banister TRIMP [Published: Banister 1991], `TRIMP = min x HRr x 0.64 e^(1.92 HRr)` (men) or `0.86 e^(1.67 HRr)` (women), where `HRr = (HRavg - HRrest)/(HRmax - HRrest)`. Converted to LU with `k = 3.5` by default. **k is re-fitted per user** as the median ratio of sRPE load to TRIMP over sessions that have both. If both exist for a session, the app averages them.
3. **Watch calories only.** `MET = active_kcal / (kg x hours) + 1` (net-energy relation from the Compendium of Physical Activities, Ainsworth 2011; Herrmann 2024). Then `RPE_est = clamp(MET / 1.4, 1, 10)` [Heuristic mapping, sanity checked: walking 3.5 MET -> RPE 2.5, running ~10 MET -> RPE 7], and `LU = RPE_est x minutes`. Flagged low-confidence.
4. **Lifting sessions** use sRPE, with the mean of working-set RPE as the fallback when no session RPE is given.

Worked: 40 min, watch only, 400 kcal, 75 kg -> MET 9.0 -> RPE_est 6.4 -> LU 257. With RPE 6 and HR: 242.

## 6. Fitness, Fatigue, Form [Published: Banister 1975; Coggan performance-management model]

```
daily LU series L(t)
Fitness (CTL) = exponentially weighted average, tau = 42 days
Fatigue (ATL) = exponentially weighted average, tau = 7 days
Form          = Fitness - Fatigue        (positive = fresh, strongly negative = overreaching)
```

- Used as a **readiness guide**, not an injury predictor. The acute:chronic workload ratio is deliberately not used because its predictive value is disputed (Impellizzeri 2020).
- Start-up: both averages begin at the first day's load and settle within ~2 weeks, so show "calibrating" for the first 14 days.
- **Time Bank cap:** when Form is below a user-set floor (default -25% of Fitness), workout earnings for that day are capped. This stops the app rewarding overtraining.

## 7. Session report (what the user sees)

- **Stimulus:** total HSE and HSE per muscle against the weekly band.
- **Set quality:** `HSE / working sets` as a percentage (how many sets were truly hard).
- **Progress:** change in e1RM per exercise against its reference, with PR flags.
- **Load:** LU for the session, and Form after it.
- **Confidence** tag on anything built from assumptions.

I deliberately did not add a single 0-100 "effectiveness score". Any such number would be arbitrary and give a false sense of precision. If you want a score for motivation, I'd define it as `plan completion % (HSE done / HSE planned)` and label it as a game metric.

## 8. Verified worked examples (output of `effective_load.py`)

| # | Case | Result |
|---|---|---|
| 1 | Bench 60 kg x 8 @ RPE 8 | RIR 2, e1RM 80.0 kg, HSE 0.60 |
| 2 | Squat 100 kg x 5 to failure | e1RM 114.6 kg, HSE 1.00 |
| 3 | Unknown "cable crunch" 40 kg x 15, no RPE, no history | e1RM not computed (RTF 17), HSE 0.60, confidence low |
| 4 | Unknown "landmine press" (vertical push) 30 kg x 10 @ RPE 9 | e1RM 41.3 kg, HSE 0.80 -> front delts 0.80, triceps 0.40, chest 0.40 |
| 5 | Weighted pull-up, BW 75 kg + 10 kg, 6 reps, RIR 1 | load 85 kg, e1RM 103.4 kg, HSE 0.80 |
| 6 | Light set 20 kg x 10 vs reference 100 kg | I = 0.20, HSE 0.40 (stimulus fades below 30%) |
| 7 | Watch-only activity, 40 min, 400 kcal, 75 kg | MET 9.0, RPE_est 6.4, LU 257 |

## 9. Validation plan (so it isn't just a guess)

- **Personal back-test:** after ~8 weeks, compare weekly HSE per muscle with e1RM and measurement trends. If high-HSE weeks don't track progress, tune `EFFECTIVE_WINDOW`, `DEFAULT_RIR` and the secondary weight in settings.
- **Cross-check:** where sRPE and HR both exist, large disagreement between them triggers a "check your RPE" hint.
- **Log the confidence label** with every stored value, so low-confidence data can be filtered out of trends.

## 10. References (verify before publishing)

- Banister EW et al. (1975) impulse-response model; Banister (1991) TRIMP.
- Brzycki M. (1993) J Phys Educ Recreat Dance. Epley B. (1985) Poundage chart.
- Zourdos MC et al. (2016) J Strength Cond Res, RIR-based RPE scale. Helms ER et al. (2016) Strength Cond J.
- Halperin I et al. (2022) Sports Med, accuracy of predicting reps to failure.
- Schoenfeld BJ, Ogborn D, Krieger JW (2017) J Sports Sci, dose-response of weekly sets and hypertrophy.
- Schoenfeld BJ et al. (2017) J Strength Cond Res, low vs high load. Lopez P et al. (2021) Med Sci Sports Exerc, load network meta-analysis.
- Refalo MC et al. (2023) Sports Med, proximity to failure and hypertrophy. Robinson ZP et al. (2024) Sports Med.
- Pelland JC et al. (2024) preprint, resistance training dose-response meta-regression.
- Foster C et al. (2001) J Strength Cond Res, session RPE.
- Ainsworth BE et al. (2011) Compendium of Physical Activities; Herrmann SD et al. (2024) 2024 update.
- Ebben WP et al. (2011) J Strength Cond Res, push-up kinetics. Impellizzeri FM et al. (2020), critique of the acute:chronic workload ratio.

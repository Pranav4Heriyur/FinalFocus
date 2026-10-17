"""
Effective Load Model v1 (reference implementation, port to Kotlin later).
Every constant that is a heuristic (not a published value) is marked HEURISTIC and is user-tunable.
"""
import math

# ---------- constants ----------
EFFECTIVE_WINDOW = 5        # HEURISTIC: reps within ~5 of failure are the stimulating reps
DEFAULT_RIR = 2             # HEURISTIC: assumed when RPE not logged (people stop ~1-3 reps short; Halperin 2022)
MIN_INTENSITY = 0.30        # ~30% 1RM floor for full stimulus when taken near failure (Schoenfeld 2017; Lopez 2021)
MAX_RTF_FOR_1RM = 15        # e1RM formulas degrade above ~10-15 reps
ASSUMED_I_HIGH_REP = 0.45   # HEURISTIC: relative intensity assumed for >15-rep sets with no history
TRIMP_TO_LU = 3.5           # default scale Banister TRIMP -> sRPE load units; re-fit per user
SECONDARY_WEIGHT = 0.5      # HEURISTIC: fractional crediting of secondary muscles


def rir_from(rpe=None, set_type="normal"):
    """RIR from RPE (Zourdos 2016): RIR = 10 - RPE. Failure sets = 0."""
    if set_type == "failure":
        return 0
    if rpe is not None:
        return max(0, round(10 - rpe))
    return DEFAULT_RIR


def effective_load_kg(external_kg, bodyweight_kg=0.0, bw_factor=0.0, assist_kg=0.0):
    """Load actually moved. Weighted/bodyweight/assisted all reduce to this."""
    return max(0.0, external_kg + bodyweight_kg * bw_factor - assist_kg)


def e1rm(load, reps, rir):
    """Mean of Epley (1985) and Brzycki (1993) on reps-to-failure RTF = reps + RIR. None if unreliable (>15)."""
    rtf = reps + rir
    if rtf > MAX_RTF_FOR_1RM or load <= 0:
        return None
    if rtf == 1:
        return load
    epley = load * (1 + rtf / 30)
    brzycki = load * 36 / (37 - rtf)
    return (epley + brzycki) / 2


def hard_set_equivalent(reps, rir, load, e1rm_ref=None, own_e1rm=None):
    """
    HSE = min(reps, 5 - RIR)/5 * g(I), g(I)=min(1, I/0.30).
    I = load / reference e1RM (personal best over last ~8 weeks); falls back to this set's own e1RM,
    then to an assumed value for very high-rep sets.
    Returns (hse, relative_intensity, confidence).
    """
    ref = e1rm_ref or own_e1rm
    if ref:
        intensity = min(1.0, load / ref)
        confidence = "high" if e1rm_ref else "medium"
    else:
        intensity, confidence = ASSUMED_I_HIGH_REP, "low"
    er = max(0, min(reps, EFFECTIVE_WINDOW - rir))
    g = min(1.0, intensity / MIN_INTENSITY)
    return (er / EFFECTIVE_WINDOW) * g, intensity, confidence


# ---------- muscle credit templates for UNKNOWN exercises (HEURISTIC; EMG does not map 1:1 to growth) ----------
PATTERNS = {
    "horizontal_push": {"chest": 1.0, "triceps": 0.5, "front_delts": 0.5},
    "vertical_push":   {"front_delts": 1.0, "triceps": 0.5, "chest": 0.5},
    "vertical_pull":   {"lats": 1.0, "biceps": 0.5, "upper_back": 0.5},
    "horizontal_pull": {"upper_back": 1.0, "lats": 0.5, "biceps": 0.5, "rear_delts": 0.5},
    "squat":           {"quads": 1.0, "glutes": 0.5},
    "hinge":           {"glutes": 1.0, "hamstrings": 1.0, "lower_back": 0.5},
    "lunge":           {"quads": 1.0, "glutes": 0.5},
    "core":            {"core": 1.0},
    "carry":           {"core": 1.0, "forearms": 0.5, "traps": 0.5},
}


def muscle_credit(primary, pattern=None):
    """Isolation: primary muscle(s) at 1.0. Compound: pattern template. Always works with 1 tap."""
    if pattern in PATTERNS:
        return dict(PATTERNS[pattern])
    return {m: 1.0 for m in (primary if isinstance(primary, (list, tuple)) else [primary])}


# ---------- cross-modality session load (units: LU = sRPE x minutes) ----------
def srpe_load(rpe, minutes):
    """Foster et al. 2001."""
    return rpe * minutes


def banister_trimp(minutes, hr_avg, hr_rest, hr_max, male=True):
    """Banister 1991. HRr = heart-rate reserve fraction."""
    hrr = max(0.0, min(1.0, (hr_avg - hr_rest) / (hr_max - hr_rest)))
    a, b = (0.64, 1.92) if male else (0.86, 1.67)
    return minutes * hrr * a * math.exp(b * hrr)


def met_from_kcal(active_kcal, weight_kg, minutes):
    """Net-energy approximation: kcal = (MET-1) * kg * hours  ->  MET = kcal/(kg*h) + 1."""
    return active_kcal / (weight_kg * minutes / 60) + 1


def rpe_from_met(met):
    """HEURISTIC fallback when nothing but watch calories exist: RPE ~ MET/1.4, clamped 1..10."""
    return max(1.0, min(10.0, met / 1.4))


def session_load(minutes, rpe=None, trimp=None, met=None, k=TRIMP_TO_LU):
    """Priority: sRPE (+TRIMP average if both) > TRIMP > MET fallback."""
    parts = []
    if rpe is not None:
        parts.append(srpe_load(rpe, minutes))
    if trimp is not None:
        parts.append(trimp * k)
    if parts:
        return sum(parts) / len(parts)
    if met is not None:
        return srpe_load(rpe_from_met(met), minutes)
    raise ValueError("need at least duration + one of rpe / hr / calories")


# ---------- Fitness / Fatigue / Form (Banister 1975; Coggan PMC 42/7 d) ----------
def fitness_fatigue(daily_loads, tau_fit=42, tau_fat=7):
    ctl = atl = None
    out = []
    for load in daily_loads:
        if ctl is None:
            ctl = atl = load
        ctl += (load - ctl) * (1 - math.exp(-1 / tau_fit))
        atl += (load - atl) * (1 - math.exp(-1 / tau_fat))
        out.append((round(ctl, 1), round(atl, 1), round(ctl - atl, 1)))
    return out


# ---------- tests / worked examples ----------
if __name__ == "__main__":
    r = lambda x: None if x is None else round(x, 2)

    # 1 Bench 60kg x 8 @ RPE 8 (known exercise, no history)
    rir = rir_from(8); e = e1rm(60, 8, rir)
    print("1 bench  rir", rir, "e1RM", r(e), "HSE", r(hard_set_equivalent(8, rir, 60, own_e1rm=e)[0]))
    assert abs(e - 80) < 0.01

    # 2 Squat 100 x 5 to failure
    e = e1rm(100, 5, 0)
    print("2 squat  e1RM", r(e), "HSE", r(hard_set_equivalent(5, 0, 100, own_e1rm=e)[0]))

    # 3 UNKNOWN exercise: cable crunch 40kg x 15, no RPE, no history
    rir = rir_from(); e = e1rm(40, 15, rir)
    print("3 unknown crunch e1RM", e, "HSE/I/conf", [r(x) if not isinstance(x, str) else x for x in hard_set_equivalent(15, rir, 40, own_e1rm=e)])
    print("  muscle credit (isolation):", muscle_credit("core"))

    # 4 UNKNOWN compound: 'landmine press' classified as vertical_push, 30kg x 10 @ RPE 9
    rir = rir_from(9); e = e1rm(30, 10, rir)
    hse = hard_set_equivalent(10, rir, 30, own_e1rm=e)[0]
    print("4 landmine e1RM", r(e), "HSE", r(hse), {m: r(hse * w) for m, w in muscle_credit(None, "vertical_push").items()})

    # 5 Weighted pull-up: BW 75, factor 1.0, +10 kg, 6 reps RIR 1
    load = effective_load_kg(10, 75, 1.0); e = e1rm(load, 6, 1)
    print("5 pullup load", load, "e1RM", r(e), "HSE", r(hard_set_equivalent(6, 1, load, own_e1rm=e)[0]))

    # 6 Warm-up-ish light set (I<30%) vs reference
    print("6 light set 20kg x 10 RIR2 vs ref 100 ->", r(hard_set_equivalent(10, 2, 20, e1rm_ref=100)[0]))

    # 7 Unknown cardio/sport, 40 min, watch only: 400 kcal, 75 kg
    met = met_from_kcal(400, 75, 40)
    print("7 watch-only MET", r(met), "RPE_est", r(rpe_from_met(met)), "LU", r(session_load(40, met=met)))
    print("  with RPE 6 + HR TRIMP:", r(session_load(40, rpe=6, trimp=banister_trimp(40, 150, 55, 190))))

    # 8 Fitness / Fatigue / Form over 14 days (rest day 0)
    loads = [300, 0, 350, 280, 0, 400, 320, 0, 300, 360, 0, 380, 300, 0]
    print("8 CTL/ATL/Form last 3 days:", fitness_fatigue(loads)[-3:])
    print("OK")

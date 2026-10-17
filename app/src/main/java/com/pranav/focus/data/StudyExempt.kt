package com.pranav.focus.data

enum class ExemptKind { CHANNEL, VIDEO }

/** [id] is the stable YouTube channel id (UC…) or video id; [name] is an editable label only. */
data class StudyChannel(val id: String, val name: String, val kind: ExemptKind = ExemptKind.CHANNEL, val enabled: Boolean = true, val handle: String = "")

/** What the detector could read from YouTube's UI. Anything unknown stays null. */
data class Observed(val channelId: String? = null, val videoId: String? = null, val channelName: String? = null)

enum class Verdict { VERIFIED, NAME_MATCHED, UNVERIFIED }

object StudyExempt {
    private val CHANNEL_ID = Regex("UC[0-9A-Za-z_-]{22}")
    private val VIDEO_ID = Regex("[0-9A-Za-z_-]{11}")

    /** Extract an id from a pasted channel/video URL or raw id. @handles are returned as-is in [Parsed.handle] and carry no id. */
    data class Parsed(val kind: ExemptKind, val id: String = "", val handle: String = "")

    fun parse(input: String): Parsed? {
        val s = input.trim()
        if (s.isEmpty()) return null
        if (CHANNEL_ID.matches(s)) return Parsed(ExemptKind.CHANNEL, s)
        Regex("youtube\\.com/channel/(UC[0-9A-Za-z_-]{22})").find(s)?.let { return Parsed(ExemptKind.CHANNEL, it.groupValues[1]) }
        Regex("youtube\\.com/@([^/?#\\s]+)").find(s)?.let { return Parsed(ExemptKind.CHANNEL, handle = it.groupValues[1]) }
        Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/))([0-9A-Za-z_-]{11})").find(s)?.let { return Parsed(ExemptKind.VIDEO, it.groupValues[1]) }
        return null
    }

    /** First channel id found inside a piece of UI text (e.g. a link), else null. */
    fun findChannelId(text: String?): String? = text?.let { CHANNEL_ID.find(it)?.value }
    fun isVideoId(s: String) = VIDEO_ID.matches(s)

    /**
     * Identity rules. Only stable ids verify. A display-name match alone is NAME_MATCHED (weaker, spoofable)
     * and is never treated as verified. Disabled entries never match.
     */
    fun verdict(o: Observed?, list: List<StudyChannel>): Verdict {
        if (o == null) return Verdict.UNVERIFIED
        val on = list.filter { it.enabled }
        if (o.videoId != null && on.any { it.kind == ExemptKind.VIDEO && it.id == o.videoId }) return Verdict.VERIFIED
        if (o.channelId != null && on.any { it.kind == ExemptKind.CHANNEL && it.id.isNotEmpty() && it.id == o.channelId }) return Verdict.VERIFIED
        if (o.channelId == null && !o.channelName.isNullOrBlank() &&
            on.any { it.kind == ExemptKind.CHANNEL && it.name.trim().equals(o.channelName.trim(), ignoreCase = true) }) return Verdict.NAME_MATCHED
        return Verdict.UNVERIFIED
    }

    /** Whether a verdict exempts time. NAME_MATCHED only counts when the user explicitly opted in. */
    fun exempts(v: Verdict, allowNameMatch: Boolean) = v == Verdict.VERIFIED || (v == Verdict.NAME_MATCHED && allowNameMatch)
}

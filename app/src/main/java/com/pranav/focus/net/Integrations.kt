package com.pranav.focus.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pranav.focus.data.Store
import com.pranav.focus.lock.AppState
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    fun request(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to txt
    }

    fun form(vararg p: Pair<String, String>) = p.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
}

object NetState {
    var health by mutableStateOf("")
    var notion by mutableStateOf("")
    var busy by mutableStateOf(false)
}

object Notion {
    fun configured(ctx: Context) = Secure.get(ctx, "notion_token").isNotEmpty() && Secure.get(ctx, "notion_db").isNotEmpty()

    fun cleanDb(raw: String): String {
        val hex = raw.replace("-", "")
        return Regex("[0-9a-fA-F]{32}").findAll(hex).lastOrNull()?.value ?: raw.trim()
    }

    fun save(ctx: Context, token: String, db: String) {
        Secure.put(ctx, "notion_token", token.trim()); Secure.put(ctx, "notion_db", cleanDb(db))
    }

    fun disconnect(ctx: Context) { Secure.remove(ctx, "notion_token"); Secure.remove(ctx, "notion_db"); NetState.notion = "" }

    private fun headers(ctx: Context) = mapOf(
        "Authorization" to "Bearer ${Secure.get(ctx, "notion_token")}",
        "Notion-Version" to "2022-06-28",
        "Content-Type" to "application/json",
    )

    private fun titleProp(ctx: Context): String? {
        val (c, t) = Http.request("GET", "https://api.notion.com/v1/databases/${Secure.get(ctx, "notion_db")}", headers(ctx))
        if (c !in 200..299) return null
        val props = JSONObject(t).optJSONObject("properties") ?: return null
        for (k in props.keys()) if (props.getJSONObject(k).optString("type") == "title") return k
        return null
    }

    /** Blocking. Returns a human-readable result. */
    fun push(ctx: Context, title: String, body: String): String {
        if (!configured(ctx)) return "Notion isn’t set up."
        return runCatching {
            val prop = titleProp(ctx) ?: return "Couldn’t read the database. Share it with your integration."
            val page = JSONObject()
                .put("parent", JSONObject().put("database_id", Secure.get(ctx, "notion_db")))
                .put("properties", JSONObject().put(prop, JSONObject().put("title", JSONArray().put(JSONObject().put("text", JSONObject().put("content", title))))))
                .put("children", JSONArray().put(JSONObject().put("object", "block").put("type", "paragraph")
                    .put("paragraph", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("type", "text").put("text", JSONObject().put("content", body)))))))
            val (c, _) = Http.request("POST", "https://api.notion.com/v1/pages", headers(ctx), page.toString())
            if (c in 200..299) "Sent to Notion." else "Notion said no ($c)."
        }.getOrDefault("Network error. Try again.")
    }

    fun pushAsync(ctx: Context, title: String, body: String, report: Boolean = true) {
        Thread { val r = push(ctx, title, body); if (report) NetState.notion = r }.start()
    }
}

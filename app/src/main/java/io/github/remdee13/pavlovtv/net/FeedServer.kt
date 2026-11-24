package io.github.remdee13.pavlovtv.net

import io.github.remdee13.pavlovtv.data.CustomFeed
import io.github.remdee13.pavlovtv.net.LanServer.Companion.esc
import io.github.remdee13.pavlovtv.net.LanServer.Companion.js
import io.github.remdee13.pavlovtv.net.LanServer.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Texts for the phone page, already in the user's language. [added] contains "{name}". */
data class FeedPageText(
    val title: String,
    val hint: String,
    val placeholder: String,
    val add: String,
    val checking: String,
    val added: String,
    val notFound: String,
)

/**
 * "Add news from your phone": the phone page sends any address the user pastes (a site,
 * a YouTube channel, an RSS link). The TV finds the feed with [add] and answers with its name.
 */
class FeedServer(
    private val text: FeedPageText,
    private val add: suspend (String) -> CustomFeed?,
) {
    private val server = LanServer(MAX_BODY) { request ->
        when {
            request.method == "GET" && request.path == "" -> Response.html(page())
            request.method == "POST" && request.path == "add" -> {
                val feed = add(request.body.toString(Charsets.UTF_8).trim())
                val answer = if (feed != null) {
                    mapOf("ok" to JsonPrimitive(true), "name" to JsonPrimitive(feed.name))
                } else {
                    mapOf("ok" to JsonPrimitive(false))
                }
                Response.json(JsonObject(answer).toString())
            }
            else -> Response.text(404, "not found")
        }
    }

    /** Starts listening and returns the address to show as a QR code, or null without a network. */
    fun start(scope: CoroutineScope): String? = server.start(scope)

    fun stop() = server.stop()

    private fun page(): String = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(text.title)}</title>
<style>${LanServer.PAGE_STYLE}</style></head><body><div class="card">
<h1>${esc(text.title)}</h1><p>${esc(text.hint)}</p>
<form id="f"><input id="u" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="${esc(text.placeholder)}">
<button type="submit">${esc(text.add)}</button></form>
<div id="s"></div><ul id="l"></ul></div>
<script>
const f=document.getElementById('f'),u=document.getElementById('u'),s=document.getElementById('s'),l=document.getElementById('l');
f.onsubmit=async(e)=>{e.preventDefault();const v=u.value.trim();if(!v)return;s.textContent='${js(text.checking)}';
try{const r=await fetch(location.pathname+'/add',{method:'POST',body:v});const d=await r.json();
if(d.ok){s.textContent='${js(text.added)}'.replace('{name}',d.name);const li=document.createElement('li');li.textContent=d.name;l.appendChild(li);u.value=''}
else{s.textContent='${js(text.notFound)}'}}catch(err){s.textContent='${js(text.notFound)}'}};
</script></body></html>
""".trimIndent()

    private companion object {
        const val MAX_BODY = 4096
    }
}

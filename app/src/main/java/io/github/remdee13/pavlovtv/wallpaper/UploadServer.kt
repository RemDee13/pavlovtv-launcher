package io.github.remdee13.pavlovtv.wallpaper

import io.github.remdee13.pavlovtv.net.LanServer
import io.github.remdee13.pavlovtv.net.LanServer.Companion.esc
import io.github.remdee13.pavlovtv.net.LanServer.Companion.js
import io.github.remdee13.pavlovtv.net.LanServer.Response
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Texts for the phone page, already in the user's language. */
data class UploadPageText(val title: String, val hint: String, val choose: String, val sending: String, val done: String, val failed: String)

/**
 * "Send a photo from your phone": the phone page shrinks pictures in the browser and
 * POSTs the JPEG bytes to the TV, which saves them in the photo library.
 */
class UploadServer(
    private val library: PhotoLibrary,
    private val text: UploadPageText,
    private val onUploaded: (File) -> Unit,
) {
    private val server = LanServer(MAX_UPLOAD) { request ->
        when {
            request.method == "GET" && request.path == "" -> Response.html(page())
            request.method == "POST" && request.path == "upload" -> {
                val file = library.importBytes(request.body)
                if (file != null) {
                    onUploaded(file)
                    Response.text(200, "ok")
                } else {
                    Response.text(400, "bad image")
                }
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
<label>${esc(text.choose)}<input id="f" type="file" accept="image/*" multiple hidden></label>
<div id="s"></div></div>
<script>
const f=document.getElementById('f'),s=document.getElementById('s');
function load(file){return new Promise((ok,bad)=>{if(window.createImageBitmap){createImageBitmap(file).then(ok,()=>fallback(file).then(ok,bad))}else{fallback(file).then(ok,bad)}})}
function fallback(file){return new Promise((ok,bad)=>{const i=new Image();i.onload=()=>ok(i);i.onerror=bad;i.src=URL.createObjectURL(file)})}
async function send(file){const img=await load(file);const k=Math.min(1,${PhotoLibrary.MAX_SIDE}/Math.max(img.width,img.height));
const c=document.createElement('canvas');c.width=Math.round(img.width*k);c.height=Math.round(img.height*k);
c.getContext('2d').drawImage(img,0,0,c.width,c.height);
const blob=await new Promise(r=>c.toBlob(r,'image/jpeg',0.9));
const r=await fetch(location.pathname+'/upload',{method:'POST',body:blob});if(!r.ok)throw new Error(r.status)}
f.onchange=async()=>{for(const file of f.files){s.textContent='${js(text.sending)} '+file.name;try{await send(file);s.textContent='${js(text.done)}'}catch(e){s.textContent='${js(text.failed)}'}}};
</script></body></html>
""".trimIndent()

    private companion object {
        const val MAX_UPLOAD = 15 * 1024 * 1024
    }
}

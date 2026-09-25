package io.github.teamomuito.larpfm.lastfm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class HttpResponse(val code: Int, val body: String)

fun interface HttpTransport {
    /** Sends [form] as an `application/x-www-form-urlencoded` POST. */
    @Throws(IOException::class)
    fun post(url: String, form: Map<String, String>): HttpResponse
}

class UrlConnectionTransport(private val userAgent: String) : HttpTransport {
    override fun post(url: String, form: Map<String, String>): HttpResponse {
        val body = form.entries
            .joinToString("&") { (name, value) -> encode(name) + "=" + encode(value) }
            .toByteArray(Charsets.UTF_8)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", userAgent)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    // The Charset overload of URLEncoder.encode needs Android API 33.
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

package com.phoneassistant.app.guidance

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class RemoteGuidanceAgent(
    private val endpoint: String,
    private val fallback: GuidanceAgent,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 30_000,
) : GuidanceAgent {
    override fun decide(request: GuidanceRequest): GuidanceDecision = try {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(GuidanceJsonEncoder.encodeRequest(request))
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val responseBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                error("Guidance API returned HTTP $statusCode: $responseBody")
            }
            GuidanceJsonDecoder.decodeDecision(
                connection.inputStream.bufferedReader().use { it.readText() },
            ).also { Log.i(TAG, "Remote guidance decision received") }
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        Log.w(TAG, "Remote guidance unavailable; using local fallback", error)
        fallback.decide(request)
    }

    private companion object {
        const val TAG = "PhoneAssistGuidance"
    }
}

object GuidanceJsonDecoder {
    fun decodeDecision(json: String): GuidanceDecision {
        val root = JSONObject(json)
        val expectedJson = root.optJSONObject("expectedResult")
        return GuidanceDecision(
            status = enumValue(root.getString("status")),
            targetElementId = root.nullableString("targetElementId"),
            instruction = root.getString("instruction"),
            expectedResult = expectedJson?.let { expected ->
                ExpectedResult(
                    elementId = expected.getString("elementId"),
                    property = expected.getString("property"),
                    booleanValue = expected.getBoolean("value"),
                    successMessage = expected.getString("successMessage"),
                )
            },
            confidence = root.getDouble("confidence"),
            risk = enumValue(root.getString("risk")),
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValueOf(value.uppercase(Locale.US))

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)
}
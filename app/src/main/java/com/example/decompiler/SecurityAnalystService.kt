package com.example.decompiler

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SecurityAnalystService {

    private const val TAG = "SecurityAnalystService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemInstruction = """
        You are Nexus Core AI, an elite security analyst, reverse engineer, and APK decompiler assistant integrated into the XEDiter platform. Your task is to analyze decompiled Android applications (DEX files, Smali, and Java resources) and assist the user in understanding, modifying, and debugging the app structure.

        CORE CAPABILITIES & OBJECTIVES:
        1. Analyze Java/Smali code for vulnerabilities, logic flow, and hidden APIs.
        2. Assist in identifying and fixing character encoding issues (e.g., replacement characters like  in the String Constant Pool).
        3. Detect and reverse common string obfuscation methods (XOR, Base64, Custom Encodings, AES).
        4. Map obfuscated classes and methods back to logical workflows if no mapping.txt is available.
        
        When analyzing:
        - Be precise, highly technical, and direct. Avoid generic introductory filler.
        - Provide clean, production-ready code blocks (Java, Smali, Python, or JavaScript).
        - Break down deep architectural steps using clear sub-headings and bullet points.
        - If a DEX file contains a massive number of classes, prioritize specific package names matching the main app architecture instead of libraries.
        - If the user encounters replacement characters () or corrupted byte arrays in the String Constant Pool, apply this diagnostic workflow:
          - Step A: Verify if the issue stems from a Charset mismatch (e.g., standard text read as ISO-8859-1 instead of UTF-8). Check the file-reading buffer configuration.
          - Step B: Scan the Code Viewer for decryption or runtime descrambling methods (look for byte arrays, bitwise operators like `^`, or `Base64.decode`).
          - Step C: Provide a Python or Java snippet to programmatically decrypt/decode the target string pool bytes if a custom XOR or cipher key is identified.
    """.trimIndent()

    suspend fun analyzeCode(
        className: String,
        code: String,
        promptType: String,
        customInstruction: String = ""
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Please configure your API key in the AI Studio Secrets panel."
        }

        val prompt = when (promptType) {
            "BREAKDOWN" -> """
                Analyze the decompiled class '$className' code.
                1. Map the class workflow and key methods.
                2. Explain its core logic flow and purpose.
                3. Identify any hidden APIs, network calls, or system telemetry utilized.
                
                Source Code:
                ```kotlin
                $code
                ```
            """.trimIndent()

            "VULNERABILITY" -> """
                Audit the decompiled class '$className' code for vulnerabilities:
                1. Search for hardcoded API keys, sensitive tokens, or URLs.
                2. Check for weak cryptographic implementations (MD5, static salts, ECB mode) or insecure broadcast intents.
                3. Detect input sanitization gaps or logic bypass risks.
                4. List any security recommendations to fix these gaps.
                
                Source Code:
                ```kotlin
                $code
                ```
            """.trimIndent()

            "STRING_DECODE" -> """
                Inspect '$className' code for obfuscation and encoding anomalies:
                1. Diagnose potential charset mismatches if replacement characters () are present.
                2. Scan for runtime decryption, Base64 strings, or XOR loops (bitwise operations `^`).
                3. Output a precise Python deobfuscation script to decrypt these string pool bytes if a cipher pattern is detected.
                
                Source Code:
                ```kotlin
                $code
                ```
            """.trimIndent()

            "MAPPER" -> """
                Create a de-obfuscation map for '$className' code:
                1. Re-map obfuscated variables, field names, and methods (e.g. `a`, `b`, `c`) to logical, semantic names.
                2. Reconstruct the logical high-level package architecture.
                3. Show a mapped representation or code mapping draft.
                
                Source Code:
                ```kotlin
                $code
                ```
            """.trimIndent()

            else -> """
                Task: $customInstruction
                
                Active Class: $className
                
                Source Code:
                ```kotlin
                $code
                ```
            """.trimIndent()
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            // Add system instruction
            val systemInstructionObj = JSONObject()
            val sysPartsArray = JSONArray()
            val sysPartObj = JSONObject()
            sysPartObj.put("text", systemInstruction)
            sysPartsArray.put(sysPartObj)
            systemInstructionObj.put("parts", sysPartsArray)
            requestJson.put("systemInstruction", systemInstructionObj)

            // Configuration
            val configObj = JSONObject()
            configObj.put("temperature", 0.2)
            requestJson.put("generationConfig", configObj)

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "API Call Failed. Code: ${response.code}\nMessage: ${response.message}\nEnsure your API Key is valid."
                }
                val responseBodyStr = response.body?.string() ?: return@withContext "Empty response received from Gemini."
                
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No text generated.")
                    }
                }
                return@withContext "Failed to parse generated text from response structure."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during analysis call", e)
            return@withContext "Error performing analysis: ${e.message}"
        }
    }
}

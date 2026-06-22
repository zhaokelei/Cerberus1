package com.yiran.cerberus.util

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object WebDavClient {
    private const val TAG = "WebDavClient"
    private const val CONNECTION_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    data class WebDavResponse(
        val success: Boolean,
        val statusCode: Int,
        val body: String = "",
        val errorMessage: String = ""
    )

    data class BackupFile(
        val name: String,
        val lastModified: Long,
        val size: Long
    )

    fun uploadFile(
        baseUrl: String,
        filePath: String,
        content: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, filePath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "PUT"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", content.length.toString())
                setBasicAuth(username, password)
                doOutput = true
            }

            BufferedOutputStream(connection.outputStream).use { outputStream ->
                outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                WebDavResponse(success = true, statusCode = responseCode)
            } else {
                val errorBody = readErrorStream(connection)
                WebDavResponse(
                    success = false,
                    statusCode = responseCode,
                    errorMessage = "Upload failed: $responseCode - $errorBody"
                )
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Unknown error")
        }
    }

    fun downloadFile(
        baseUrl: String,
        filePath: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, filePath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setBasicAuth(username, password)
                doInput = true
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val content = BufferedInputStream(connection.inputStream).use { inputStream ->
                    ByteArrayOutputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.toString(StandardCharsets.UTF_8.name())
                    }
                }
                WebDavResponse(success = true, statusCode = responseCode, body = content)
            } else {
                val errorBody = readErrorStream(connection)
                WebDavResponse(
                    success = false,
                    statusCode = responseCode,
                    errorMessage = "Download failed: $responseCode - $errorBody"
                )
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Unknown error")
        }
    }

    fun deleteFile(
        baseUrl: String,
        filePath: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, filePath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "DELETE"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setBasicAuth(username, password)
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299 || responseCode == 404) {
                WebDavResponse(success = true, statusCode = responseCode)
            } else {
                val errorBody = readErrorStream(connection)
                WebDavResponse(
                    success = false,
                    statusCode = responseCode,
                    errorMessage = "Delete failed: $responseCode - $errorBody"
                )
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Unknown error")
        }
    }

    fun exists(
        baseUrl: String,
        filePath: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, filePath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setBasicAuth(username, password)
            }

            val responseCode = connection.responseCode
            when (responseCode) {
                in 200..299 -> WebDavResponse(success = true, statusCode = responseCode)
                404 -> WebDavResponse(success = false, statusCode = responseCode, errorMessage = "File not found")
                else -> {
                    val errorBody = readErrorStream(connection)
                    WebDavResponse(
                        success = false,
                        statusCode = responseCode,
                        errorMessage = "Exists check failed: $responseCode - $errorBody"
                    )
                }
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Unknown error")
        }
    }

    fun mkCol(
        baseUrl: String,
        folderPath: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, folderPath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "MKCOL"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setBasicAuth(username, password)
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299 || responseCode == 405) {
                WebDavResponse(success = true, statusCode = responseCode)
            } else {
                val errorBody = readErrorStream(connection)
                WebDavResponse(
                    success = false,
                    statusCode = responseCode,
                    errorMessage = "MKCOL failed: $responseCode - $errorBody"
                )
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Unknown error")
        }
    }

    fun listFiles(
        baseUrl: String,
        folderPath: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): List<BackupFile> {
        return try {
            val url = URL(buildUrl(baseUrl, folderPath, useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "PROPFIND"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Depth", "1")
                setBasicAuth(username, password)
                doOutput = true
            }

            val requestBody = """<?xml version="1.0" encoding="utf-8" ?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getlastmodified/>
    <D:getcontentlength/>
  </D:prop>
</D:propfind>"""

            BufferedOutputStream(connection.outputStream).use { outputStream ->
                outputStream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = BufferedInputStream(connection.inputStream).use { inputStream ->
                    ByteArrayOutputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.toString(StandardCharsets.UTF_8.name())
                    }
                }
                parsePropfindResponse(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parsePropfindResponse(responseBody: String): List<BackupFile> {
        val files = mutableListOf<BackupFile>()
        
        val displayNamePattern = Regex("<D:displayname>([^<]+)</D:displayname>")
        val lastModifiedPattern = Regex("<D:getlastmodified>([^<]+)</D:getlastmodified>")
        val contentLengthPattern = Regex("<D:getcontentlength>([^<]+)</D:getcontentlength>")
        
        val entries = responseBody.split("<D:response>")
        for (entry in entries.drop(1)) {
            val displayNameMatch = displayNamePattern.find(entry)
            val lastModifiedMatch = lastModifiedPattern.find(entry)
            val contentLengthMatch = contentLengthPattern.find(entry)
            
            if (displayNameMatch != null) {
                val fileName = displayNameMatch.groupValues[1]
                if (fileName.isNotEmpty() && !fileName.startsWith(".") && fileName.endsWith(".cerb")) {
                    val lastModified = try {
                        lastModifiedMatch?.groupValues?.get(1)?.let { parseHttpDate(it) } ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                    val size = try {
                        contentLengthMatch?.groupValues?.get(1)?.toLong() ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                    files.add(BackupFile(fileName, lastModified, size))
                }
            }
        }
        
        return files.sortedByDescending { it.lastModified }
    }

    private fun parseHttpDate(dateString: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd-MMM-yy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss yyyy"
        )
        for (format in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                return sdf.parse(dateString)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
        useHttps: Boolean = true
    ): WebDavResponse {
        return try {
            val url = URL(buildUrl(baseUrl, "", useHttps))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "PROPFIND"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Depth", "0")
                setBasicAuth(username, password)
                doOutput = true
            }

            val requestBody = """<?xml version="1.0" encoding="utf-8" ?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
  </D:prop>
</D:propfind>"""

            BufferedOutputStream(connection.outputStream).use { outputStream ->
                outputStream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                WebDavResponse(success = true, statusCode = responseCode)
            } else {
                val errorBody = readErrorStream(connection)
                WebDavResponse(
                    success = false,
                    statusCode = responseCode,
                    errorMessage = "Connection test failed: $responseCode - $errorBody"
                )
            }
        } catch (e: Exception) {
            WebDavResponse(success = false, statusCode = -1, errorMessage = e.message ?: "Connection failed")
        }
    }

    private fun buildUrl(baseUrl: String, path: String, useHttps: Boolean): String {
        var url = baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (useHttps) "https://$url" else "http://$url"
        }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url + path.trim().removePrefix("/")
    }

    private fun HttpURLConnection.setBasicAuth(username: String, password: String) {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        setRequestProperty("Authorization", "Basic $encoded")
    }

    private fun readErrorStream(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun InputStream.copyTo(output: OutputStream): Long {
        val buffer = ByteArray(8192)
        var bytesCopied = 0L
        var bytes = read(buffer)
        while (bytes >= 0) {
            output.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = read(buffer)
        }
        return bytesCopied
    }
}
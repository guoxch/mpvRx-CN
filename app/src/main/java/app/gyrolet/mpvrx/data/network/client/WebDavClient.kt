/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.network.client

import android.net.Uri
import app.gyrolet.mpvrx.domain.network.NetworkConnection
import app.gyrolet.mpvrx.domain.network.NetworkFile
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.InputStream
import java.net.URLEncoder

class WebDavClient(
  private val connection: NetworkConnection,
) : NetworkClient {
  companion object {
    private const val TAG = "WebDavClient"
    private val rangeHttpClient by lazy { OkHttpClient() }
  }

  // Note: Sardine-Android uses OkHttp which properly handles UTF-8 encoding by default
  private var sardine: Sardine? = null

  /**
   * Build full WebDAV URL from connection and relative path
   * Standard approach: protocol://host:port/basePath/relativePath
   * relativePath should be relative to the basePath (connection.path)
   */
  private fun buildUrl(relativePath: String): String {
    val protocol = if (connection.useHttps) "https" else "http"
    val basePath = connection.path.trim('/')
    val cleanPath = relativePath.trim('/')

    // URL-encode each path segment to handle special chars like [ ] 中文
    fun encodePath(path: String) = path.split('/')
      .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    // If relativePath is "/" or empty, it means we're at the root of the connection
    // In that case, just use the basePath (connection.path)
    return when {
      cleanPath.isEmpty() || cleanPath == "/" -> {
        if (basePath.isEmpty()) {
          "$protocol://${connection.host}:${connection.port}/"
        } else {
          "$protocol://${connection.host}:${connection.port}/${encodePath(basePath)}/"
        }
      }
      basePath.isEmpty() -> "$protocol://${connection.host}:${connection.port}/${encodePath(cleanPath)}"
      else -> "$protocol://${connection.host}:${connection.port}/${encodePath(basePath)}/${encodePath(cleanPath)}"
    }
  }

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val client = OkHttpSardine()
        if (!connection.isAnonymous) {
          client.setCredentials(connection.username, connection.password)
        }

        // Validate with WebDAV's native PROPFIND operation. Some compliant servers do not
        // implement HEAD, which Sardine's exists() uses.
        val testUrl = buildUrl("")
        if (client.list(testUrl, 0).isEmpty()) {
          throw IllegalStateException("WebDAV base path returned no resources")
        }

        sardine = client
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      sardine = null
    }
  }

  override suspend fun deleteFile(path: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val url = buildUrl(path)
        val request = Request.Builder()
          .url(url)
          .apply {
            if (!connection.isAnonymous) {
              addHeader("Authorization", Credentials.basic(connection.username, connection.password))
            }
          }
          .delete()
          .build()
        val response = OkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
          Result.success(Unit)
        } else {
          Result.failure(Exception("删除失败: HTTP ${response.code}"))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override fun isConnected(): Boolean = sardine != null

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        val url = buildUrl(path)
        val resources = client.list(url)

        val files =
          resources
            .drop(1) // Skip the directory itself
            .map { resource: DavResource ->
              val resourceName = resource.name ?: ""

              // Build child path by appending filename to current path
              val filePath =
                if (path.isEmpty() || path == "/") {
                  resourceName
                } else {
                  "${path.trimEnd('/')}/$resourceName"
                }

              NetworkFile(
                name = resourceName,
                path = filePath,
                isDirectory = resource.isDirectory,
                size = resource.contentLength ?: 0,
                lastModified = resource.modified?.time ?: 0,
                mimeType = if (!resource.isDirectory) getMimeType(resourceName) else null,
              )
            }

        // Fallback: if Sardine returns nothing, try raw PROPFIND
        // Sardine's XML parser may drop entries with [ ] in filenames
        val result = if (files.isEmpty()) rawPropfindFiles(path, url) else files
        Result.success(result)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Raw PROPFIND fallback — bypasses Sardine's XML parser to handle [ ] in filenames
   */
  private fun rawPropfindFiles(path: String, url: String): List<NetworkFile> {
    return try {
      val xmlBody = """<?xml version="1.0" encoding="utf-8"?>
        |<D:propfind xmlns:D="DAV:">
        |  <D:prop>
        |    <D:displayname/>
        |    <D:getcontentlength/>
        |    <D:getlastmodified/>
        |    <D:getcontenttype/>
        |    <D:resourcetype/>
        |  </D:prop>
        |</D:propfind>""".trimMargin()

      val request = Request.Builder()
        .url(url)
        .addHeader("Depth", "1")
        .apply {
          if (!connection.isAnonymous) {
            addHeader(
              "Authorization",
              Credentials.basic(connection.username, connection.password),
            )
          }
        }
        .method("PROPFIND", xmlBody.toRequestBody("application/xml".toMediaType()))
        .build()

      val response = OkHttpClient().newCall(request).execute()
      val body = response.body?.string() ?: return emptyList()

      // Regex-based extraction — avoids XML parser issues with special chars
      val responseBlocks = body.split("<D:response>").drop(1)
      val dirName = path.trim('/').substringAfterLast('/')

      responseBlocks.mapNotNull { block ->
        val href = Regex("<D:href>(.*?)</D:href>").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
        val name = Regex("<D:displayname>(.*?)</D:displayname>").find(block)?.groupValues?.get(1)
          ?: href.substringAfterLast('/').trim('/')
        val isDir = block.contains("<D:collection/>")
        val size = Regex("<D:getcontentlength>(\\d+)</D:getcontentlength>").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val mime = Regex("<D:getcontenttype>(.*?)</D:getcontenttype>").find(block)?.groupValues?.get(1)

        // Skip self directory entry
        if (name.isEmpty() || name == dirName) return@mapNotNull null

        val filePath = if (path.isEmpty() || path == "/") name
        else "${path.trimEnd('/')}/$name"

        NetworkFile(
          name = name,
          path = filePath,
          isDirectory = isDir,
          size = size,
          lastModified = 0,
          mimeType = if (!isDir) mime?.takeIf { it.isNotBlank() } ?: getMimeType(name) else null,
        )
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  /**
   * Get file size for a specific file path
   * This is useful for the proxy server to support range requests
   */
  override suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        val url = buildUrl(path)

        // Use PROPFIND to get file properties including size
        val resources = client.list(url, 0) // depth 0 = only the resource itself
        if (resources.isNotEmpty() && !resources[0].isDirectory) {
          val size = resources[0].contentLength ?: -1L
          Result.success(size)
        } else {
          Result.failure(Exception("File not found or is a directory"))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileStream(
    path: String,
    offset: Long,
  ): Result<InputStream> =
    withContext(Dispatchers.IO) {
      try {
        if (offset > 0L) {
          return@withContext getRangedFileStream(path, offset)
        }

        // Create a fresh Sardine client for this stream to avoid connection conflicts
        val streamClient = OkHttpSardine()

        if (!connection.isAnonymous) {
          streamClient.setCredentials(connection.username, connection.password)
        }

        val url = buildUrl(path)
        val rawStream = streamClient.get(url)

        if (rawStream == null) {
          return@withContext Result.failure(Exception("Failed to open WebDAV stream"))
        }

        // Wrap the stream
        val wrappedStream =
          object : InputStream() {
            override fun read(): Int = rawStream.read()

            override fun read(b: ByteArray): Int = rawStream.read(b)

            override fun read(
              b: ByteArray,
              off: Int,
              len: Int,
            ): Int = rawStream.read(b, off, len)

            override fun available(): Int = rawStream.available()

            override fun close() {
              try {
                rawStream.close()
              } catch (e: Exception) {
                // Ignore
              }
            }
          }

        Result.success(wrappedStream)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getRangedFileStream(
    path: String,
    offset: Long,
  ): Result<InputStream> {
    val requestBuilder =
      Request
        .Builder()
        .url(buildUrl(path))
        .get()
        .addHeader("Range", "bytes=$offset-")

    if (!connection.isAnonymous) {
      requestBuilder.addHeader(
        "Authorization",
        Credentials.basic(connection.username, connection.password),
      )
    }

    val response = rangeHttpClient.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful && response.code != 206) {
      response.close()
      return Result.failure(Exception("Failed to open ranged WebDAV stream: HTTP ${response.code}"))
    }

    val rawStream = response.body.byteStream()
    val wrappedStream =
      object : InputStream() {
        override fun read(): Int = rawStream.read()

        override fun read(b: ByteArray): Int = rawStream.read(b)

        override fun read(
          b: ByteArray,
          off: Int,
          len: Int,
        ): Int = rawStream.read(b, off, len)

        override fun available(): Int = rawStream.available()

        override fun close() {
          runCatching { rawStream.close() }
          runCatching { response.close() }
        }
      }
    return Result.success(wrappedStream)
  }

  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        val protocol = if (connection.useHttps) "https" else "http"
        val basePath = connection.path.trim('/')
        val cleanPath = path.trim('/')

        val fullPath =
          when {
            cleanPath.isEmpty() -> basePath
            basePath.isEmpty() -> cleanPath
            else -> "$basePath/$cleanPath"
          }

        // Build WebDAV URI with credentials embedded for mpv
        val uriString =
          if (connection.isAnonymous) {
            "$protocol://${connection.host}:${connection.port}/$fullPath"
          } else {
            "$protocol://${connection.username}:${connection.password}@${connection.host}:${connection.port}/$fullPath"
          }

        Result.success(Uri.parse(uriString))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getMimeType(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
      "mp4", "m4v", "m4s" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "mpeg", "mpg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "ts", "m2ts" -> "video/mp2t"
      else -> null
    }
  }
}

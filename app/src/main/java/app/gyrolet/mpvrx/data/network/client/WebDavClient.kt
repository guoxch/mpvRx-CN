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
import app.gyrolet.mpvrx.domain.network.NetworkPath
import app.gyrolet.mpvrx.network.SharedHttpClient
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class WebDavClient(
  private val connection: NetworkConnection,
) : NetworkClient {
  companion object {
    private val rangeHttpClient by lazy {
      SharedHttpClient.derive {
        // Range reads feed the player; a stalled socket must fail fast rather than hang the stream.
        callTimeout(60, TimeUnit.SECONDS)
      }
    }
    private val contentRangePattern = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
  }

  private var sardine: Sardine? = null

  /** Builds a URL from decoded path segments so credentials and reserved characters cannot leak. */
  private fun buildUrl(
    relativePath: String,
    trailingSlash: Boolean = false,
  ): String {
    val host = connection.host.trim().removePrefix("[").removeSuffix("]")
    val builder =
      HttpUrl
        .Builder()
        .scheme(if (connection.useHttps) "https" else "http")
        .host(host)
        .port(connection.port)

    NetworkPath.from(connection.path).segments.forEach(builder::addPathSegment)
    NetworkPath.from(relativePath).segments.forEach(builder::addPathSegment)
    if (trailingSlash) builder.addPathSegment("")
    return builder.build().toString()
  }

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val candidate = OkHttpSardine()
        if (!connection.isAnonymous) {
          candidate.setCredentials(connection.username, connection.password)
        }

        if (candidate.list(buildUrl("", trailingSlash = true), 0).isEmpty()) {
          throw IOException("WebDAV base path returned no resources")
        }

        sardine = candidate
        Result.success(Unit)
      } catch (cancellation: CancellationException) {
        sardine = null
        throw cancellation
      } catch (error: Exception) {
        sardine = null
        Result.failure(error)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      sardine = null
    }
  }

  override fun isConnected(): Boolean = sardine != null

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(IOException("Not connected"))
        val directory = NetworkPath.from(path)
        val directoryUrl = buildUrl(directory.value, trailingSlash = true)
        val requestedWirePath = java.net.URI(directoryUrl).path.trimEnd('/')
        val resources = client.list(directoryUrl)

        val files =
          resources
            .filterNot { resource -> resource.path.trimEnd('/') == requestedWirePath }
            .mapNotNull { resource: DavResource ->
              val resourceName = resource.name?.trimEnd('/')?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
              runCatching {
                val filePath = directory.child(resourceName)
                NetworkFile(
                  name = resourceName,
                  path = filePath.value,
                  isDirectory = resource.isDirectory,
                  size = resource.contentLength ?: -1L,
                  lastModified = resource.modified?.time ?: 0,
                  mimeType = if (!resource.isDirectory) NetworkMimeTypes.forFileName(resourceName) else null,
                )
              }.getOrNull()
            }

        val result =
          if (files.isEmpty()) {
            rawPropfindFiles(directory, directoryUrl)
          } else {
            files
          }

        Result.success(result)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  /**
   * Raw PROPFIND fallback — bypasses Sardine's XML parser which may drop entries with [ ] in filenames.
   */
  private fun rawPropfindFiles(
    directory: NetworkPath,
    url: String,
  ): List<NetworkFile> {
    return try {
      val xmlBody =
        """<?xml version="1.0" encoding="utf-8"?>
        |<D:propfind xmlns:D="DAV:">
        |  <D:prop>
        |    <D:displayname/>
        |    <D:getcontentlength/>
        |    <D:getlastmodified/>
        |    <D:getcontenttype/>
        |    <D:resourcetype/>
        |  </D:prop>
        |</D:propfind>""".trimMargin()

      val requestBuilder =
        Request
          .Builder()
          .url(url)
          .addHeader("Depth", "1")
          .method("PROPFIND", xmlBody.toRequestBody("application/xml".toMediaType()))

      if (!connection.isAnonymous) {
        requestBuilder.addHeader("Authorization", Credentials.basic(connection.username, connection.password))
      }

      val response = rangeHttpClient.newCall(requestBuilder.build()).execute()
      val body = response.use { it.body.string() }

      // Regex-based extraction — avoids XML parser issues with special chars
      val responseBlocks = body.split("<D:response>").drop(1)
      val dirName = directory.segments.lastOrNull().orEmpty()

      responseBlocks.mapNotNull { block ->
        val href = Regex("<D:href>(.*?)</D:href>").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
        val name =
          Regex("<D:displayname>(.*?)</D:displayname>").find(block)?.groupValues?.get(1)
            ?: href.substringAfterLast('/').trim('/')
        val isDir = block.contains("<D:collection/>")
        val size =
          Regex("<D:getcontentlength>(\\d+)</D:getcontentlength>").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val mime = Regex("<D:getcontenttype>(.*?)</D:getcontenttype>").find(block)?.groupValues?.get(1)

        // Skip self directory entry
        if (name.isEmpty() || name == dirName) return@mapNotNull null

        runCatching {
          val filePath = directory.child(name)
          NetworkFile(
            name = name,
            path = filePath.value,
            isDirectory = isDir,
            size = size,
            lastModified = 0,
            mimeType = if (!isDir) mime?.takeIf { it.isNotBlank() } ?: NetworkMimeTypes.forFileName(name) else null,
          )
        }.getOrNull()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  override suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val url = buildUrl(NetworkPath.from(path).value)
        val xmlBody =
          """<?xml version="1.0" encoding="utf-8"?>
          |<D:propfind xmlns:D="DAV:">
          |  <D:prop>
          |    <D:getcontentlength/>
          |  </D:prop>
          |</D:propfind>""".trimMargin()

        val requestBuilder =
          Request
            .Builder()
            .url(url)
            .addHeader("Depth", "0")
            .method("PROPFIND", xmlBody.toRequestBody("application/xml".toMediaType()))

        if (!connection.isAnonymous) {
          requestBuilder.addHeader("Authorization", Credentials.basic(connection.username, connection.password))
        }

        val response = rangeHttpClient.newCall(requestBuilder.build()).execute()
        val body = response.use { it.body.string() }
        val size =
          Regex("<D:getcontentlength>(\\d+)</D:getcontentlength>")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

        if (size != null && size >= 0L) {
          Result.success(size)
        } else {
          Result.failure(IOException("File not found or size unavailable"))
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  override suspend fun getFileStream(
    path: String,
    offset: Long,
  ): Result<InputStream> =
    withContext(Dispatchers.IO) {
      require(offset >= 0L) { "Stream offset must not be negative" }
      try {
        if (offset > 0L) {
          return@withContext getRangedFileStream(NetworkPath.from(path), offset)
        }

        val streamClient = OkHttpSardine()
        if (!connection.isAnonymous) {
          streamClient.setCredentials(connection.username, connection.password)
        }
        Result.success(streamClient.get(buildUrl(NetworkPath.from(path).value)))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  private fun getRangedFileStream(
    path: NetworkPath,
    offset: Long,
  ): Result<InputStream> {
    val requestBuilder =
      Request
        .Builder()
        .url(buildUrl(path.value))
        .get()
        .header("Range", "bytes=$offset-")

    if (!connection.isAnonymous) {
      requestBuilder.header("Authorization", Credentials.basic(connection.username, connection.password))
    }

    val response = rangeHttpClient.newCall(requestBuilder.build()).execute()
    val contentRange = response.header("Content-Range")
    val rangeMatch = contentRangePattern.matchEntire(contentRange.orEmpty())
    val returnedStart = rangeMatch?.groupValues?.get(1)?.toLongOrNull()
    val returnedEnd = rangeMatch?.groupValues?.get(2)?.toLongOrNull()

    // A successful HTTP 200 means the server ignored Range. Returning it as if it started at
    // [offset] corrupts seeking, so only a validated 206 response is accepted.
    if (response.code != 206 || returnedStart != offset || returnedEnd == null || returnedEnd < offset) {
      response.close()
      return Result.failure(
        IOException(
          if (response.code == 200) {
            "WebDAV server ignored the requested byte range"
          } else {
            "WebDAV ranged request failed with HTTP ${response.code}"
          },
        ),
      )
    }

    val rawStream = response.body.byteStream()
    return Result.success(
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
          response.close()
        }
      },
    )
  }

  /** Credential-free origin URI. Authenticated playback must use the loopback proxy. */
  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        Result.success(Uri.parse(buildUrl(NetworkPath.from(path).value)))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  override suspend fun deleteFile(path: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val url = buildUrl(NetworkPath.from(path).value)
        val requestBuilder =
          Request
            .Builder()
            .url(url)
            .delete()

        if (!connection.isAnonymous) {
          requestBuilder.addHeader("Authorization", Credentials.basic(connection.username, connection.password))
        }

        val response = rangeHttpClient.newCall(requestBuilder.build()).execute()
        if (response.isSuccessful) {
          Result.success(Unit)
        } else {
          Result.failure(IOException("WebDAV delete failed with HTTP ${response.code}"))
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }
}

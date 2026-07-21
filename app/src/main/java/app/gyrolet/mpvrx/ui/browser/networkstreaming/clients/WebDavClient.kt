package app.gyrolet.mpvrx.ui.browser.networkstreaming.clients

import android.net.Uri
import android.util.Xml
import app.gyrolet.mpvrx.domain.network.NetworkConnection
import app.gyrolet.mpvrx.domain.network.NetworkFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WebDavClient(private val connection: NetworkConnection) : NetworkClient {
  companion object {
    private val httpClient by lazy { OkHttpClient() }

    // RFC 7231 defines three HTTP-date formats; servers are supposed to use the first,
    // but in the wild we observe all three.
    private val HTTP_DATE_FORMATS = arrayOf(
      "EEE, dd MMM yyyy HH:mm:ss zzz",
      "EEEE, dd-MMM-yy HH:mm:ss zzz",
      "EEE MMM d HH:mm:ss yyyy",
    )
  }

  // Tracks whether connect() has succeeded. We do not hold a long-lived Sardine
  // client anymore; every operation issues its own OkHttp request.
  private var authenticated = false

  /**
   * Build a properly URL-encoded [HttpUrl] from the connection and a relative path.
   *
   * Each path segment is percent-encoded by [HttpUrl.Builder.addPathSegment], so
   * characters that are illegal in a URL path (spaces, '[', ']', etc.) are
   * transmitted correctly to the server. This also avoids the strict
   * `java.net.URI` parsing that Sardine performs on response hrefs.
   */
  private fun buildHttpUrl(relativePath: String): HttpUrl {
    val protocol = if (connection.useHttps) "https" else "http"
    val basePath = connection.path.trim('/')
    val cleanPath = relativePath.trim('/')

    val builder = HttpUrl.Builder()
      .scheme(protocol)
      .host(connection.host)
      .port(connection.port)

    val segments = buildList {
      if (basePath.isNotEmpty()) addAll(basePath.split('/'))
      if (cleanPath.isNotEmpty() && cleanPath != "/") addAll(cleanPath.split('/'))
    }

    for (segment in segments) {
      // addPathSegment percent-encodes illegal characters and preserves '/' as a
      // literal separator inside a segment if `encodeSegments` is true (default).
      builder.addPathSegment(segment)
    }

    // Ensure the URL ends with '/' when listing a directory; some servers require it.
    if (relativePath.isEmpty() || relativePath == "/" || relativePath.endsWith("/")) {
      builder.addPathSegment("")
    }

    return builder.build()
  }

  private fun addAuthHeader(requestBuilder: Request.Builder) {
    if (!connection.isAnonymous) {
      requestBuilder.addHeader(
        "Authorization",
        Credentials.basic(connection.username, connection.password),
      )
    }
  }

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        // Validate the connection by issuing a depth-0 PROPFIND against the base path.
        val result = propfind("", depth = 0)
        val resources = result.getOrElse { return@withContext Result.failure(it) }
        if (resources.isEmpty()) {
          throw IllegalStateException("WebDAV base path returned no resources")
        }
        authenticated = true
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      authenticated = false
    }
  }

  override fun isConnected(): Boolean = authenticated

  /**
   * Send a PROPFIND request and parse the multistatus response ourselves.
   *
   * We intentionally do NOT use Sardine's `list()` here. Sardine constructs a
   * `java.net.URI` from each `<D:href>` element, which throws
   * `URISyntaxException` when the href contains unencoded reserved characters
   * such as `[`, `]` or space. Sardine catches that exception and silently
   * drops the resource, so files whose names contain those characters never
   * appear in the listing.
   *
   * Parsing the XML directly lets us recover every href regardless of whether
   * the server percent-encoded it, and lets us URL-decode the href to obtain
   * the real file name.
   */
  private fun propfind(path: String, depth: Int): Result<List<WebDavEntry>> {
    return try {
      val url = buildHttpUrl(path)

      val requestBody = """<?xml version="1.0" encoding="utf-8"?>
        |<D:propfind xmlns:D="DAV:">
        |  <D:prop>
        |    <D:displayname/>
        |    <D:resourcetype/>
        |    <D:getcontentlength/>
        |    <D:getcontenttype/>
        |    <D:getlastmodified/>
        |  </D:prop>
        |</D:propfind>""".trimMargin()

      val requestBuilder = Request.Builder()
        .url(url)
        .method(
          "PROPFIND",
          requestBody.toRequestBody("application/xml; charset=utf-8".toMediaType()),
        )
        .addHeader("Depth", if (depth == 0) "0" else "1")

      addAuthHeader(requestBuilder)

      httpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) {
          return Result.failure(Exception("PROPFIND failed: HTTP ${response.code}"))
        }

        val body = response.body?.string()
          ?: return Result.failure(Exception("Empty PROPFIND response body"))

        Result.success(parseMultistatus(body))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Parse a WebDAV multistatus XML response into a list of [WebDavEntry].
   *
   * The parser is namespace-agnostic (matches local names only) because
   * different servers use different namespace prefixes (D:, d:, lp1:, ...).
   * We strip any namespace prefix from [XmlPullParser.getName] manually so
   * the parser does not need to be namespace-aware (which is stricter and
   * would reject malformed responses from some servers).
   */
  private fun parseMultistatus(xml: String): List<WebDavEntry> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(xml.reader())

    val entries = mutableListOf<WebDavEntry>()
    var current: WebDavEntry? = null
    val textBuilder = StringBuilder()
    var inResourceType = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          val name = parser.name?.substringAfter(':')?.lowercase(Locale.ROOT) ?: ""
          textBuilder.setLength(0)

          when (name) {
            "response" -> current = WebDavEntry(
              href = "",
              displayName = null,
              isDirectory = false,
              contentLength = null,
              contentType = null,
              lastModified = null,
            )
            "resourcetype" -> inResourceType = true
            "collection" -> {
              if (inResourceType) {
                current = current?.copy(isDirectory = true)
              }
            }
          }
        }
        XmlPullParser.TEXT -> {
          textBuilder.append(parser.text ?: "")
        }
        XmlPullParser.END_TAG -> {
          val name = parser.name?.substringAfter(':')?.lowercase(Locale.ROOT) ?: ""
          val text = textBuilder.toString().trim()

          val entry = current
          if (entry != null) {
            val updated = when (name) {
              "href" -> entry.copy(href = text)
              "displayname" -> entry.copy(displayName = text.takeIf { it.isNotEmpty() })
              "getcontentlength" -> entry.copy(contentLength = text.toLongOrNull())
              "getcontenttype" -> entry.copy(contentType = text.takeIf { it.isNotEmpty() })
              "getlastmodified" -> entry.copy(lastModified = text.takeIf { it.isNotEmpty() })
              "resourcetype" -> entry // already handled via <collection/>
              else -> entry
            }
            if (name == "response") {
              entries.add(updated)
              current = null
            } else {
              current = updated
            }
          }

          if (name == "resourcetype") inResourceType = false
          textBuilder.setLength(0)
        }
      }
      event = parser.next()
    }

    return entries
  }

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val result = propfind(path, depth = 1)
        val resources = result.getOrElse { return@withContext Result.failure(it) }

        // First entry is the directory itself; skip it.
        val files = resources.drop(1).mapNotNull { entry ->
          val resourceName = entry.displayName?.takeIf { it.isNotEmpty() }
            ?: deriveNameFromHref(entry.href)
            ?: return@mapNotNull null

          if (resourceName.isEmpty()) return@mapNotNull null

          val filePath = if (path.isEmpty() || path == "/") {
            resourceName
          } else {
            "${path.trimEnd('/')}/$resourceName"
          }

          NetworkFile(
            name = resourceName,
            path = filePath,
            isDirectory = entry.isDirectory,
            size = entry.contentLength ?: 0,
            lastModified = parseHttpDate(entry.lastModified)?.time ?: 0,
            mimeType = if (!entry.isDirectory) getMimeType(resourceName) else null,
          )
        }

        Result.success(files)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Extract the last path segment from a WebDAV href and URL-decode it.
   *
   * Servers return hrefs in one of two forms:
   *   - percent-encoded:  `/dav/path/file%20%5B1%5D.mkv`
   *   - raw:              `/dav/path/file [1].mkv`
   *
   * `Uri.decode` handles the encoded form; the raw form is returned unchanged.
   * Either way, the resulting file name preserves spaces, '[' and ']'.
   */
  private fun deriveNameFromHref(href: String): String? {
    if (href.isEmpty()) return null

    val decoded = try {
      Uri.decode(href)
    } catch (e: Exception) {
      href
    }

    // Strip query and fragment if present.
    val withoutQuery = decoded.substringBefore('?').substringBefore('#')
    val trimmed = withoutQuery.trimEnd('/')
    if (trimmed.isEmpty()) return null

    val lastSegment = trimmed.substringAfterLast('/', missingDelimiterValue = "")
    return lastSegment.ifEmpty { trimmed }
  }

  private fun parseHttpDate(dateString: String?): java.util.Date? {
    if (dateString.isNullOrEmpty()) return null
    for (format in HTTP_DATE_FORMATS) {
      try {
        val sdf = SimpleDateFormat(format, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.parse(dateString)
      } catch (e: Exception) {
        // try next format
      }
    }
    return null
  }

  override suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val result = propfind(path, depth = 0)
        val resources = result.getOrElse { return@withContext Result.failure(it) }

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

  override suspend fun getFileStream(path: String, offset: Long): Result<InputStream> =
    withContext(Dispatchers.IO) {
      try {
        if (offset > 0L) {
          return@withContext getRangedFileStream(path, offset)
        }

        // Issue a GET via OkHttp so the URL path is properly encoded for files
        // whose names contain spaces, '[' or ']'.
        val requestBuilder = Request.Builder()
          .url(buildHttpUrl(path))
          .get()

        addAuthHeader(requestBuilder)

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
          response.close()
          return@withContext Result.failure(
            Exception("Failed to open WebDAV stream: HTTP ${response.code}"),
          )
        }

        val rawStream = response.body.byteStream()
        val wrappedStream = object : InputStream() {
          override fun read(): Int = rawStream.read()
          override fun read(b: ByteArray): Int = rawStream.read(b)
          override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)
          override fun available(): Int = rawStream.available()

          override fun close() {
            runCatching { rawStream.close() }
            runCatching { response.close() }
          }
        }

        Result.success(wrappedStream)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getRangedFileStream(path: String, offset: Long): Result<InputStream> {
    val requestBuilder = Request.Builder()
      .url(buildHttpUrl(path))
      .get()
      .addHeader("Range", "bytes=$offset-")

    addAuthHeader(requestBuilder)

    val response = httpClient.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful && response.code != 206) {
      response.close()
      return Result.failure(
        Exception("Failed to open ranged WebDAV stream: HTTP ${response.code}"),
      )
    }

    val rawStream = response.body.byteStream()
    val wrappedStream = object : InputStream() {
      override fun read(): Int = rawStream.read()
      override fun read(b: ByteArray): Int = rawStream.read(b)
      override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)
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

        // URL-encode each path segment so mpv receives a valid URL even when
        // file names contain spaces, '[' or ']'.
        val segments = buildList {
          if (basePath.isNotEmpty()) addAll(basePath.split('/'))
          if (cleanPath.isNotEmpty()) addAll(cleanPath.split('/'))
        }
        val encodedPath = segments.joinToString("/") { segment ->
          Uri.encode(segment, "/")
        }
        val fullPath = if (encodedPath.isEmpty()) "" else "/$encodedPath"

        // Embed credentials in the URI for mpv. Username and password may
        // contain reserved characters, so encode them as well.
        val userInfo = if (connection.isAnonymous) {
          ""
        } else {
          val user = Uri.encode(connection.username)
          val pass = Uri.encode(connection.password)
          "$user:$pass@"
        }

        Result.success(
          Uri.parse("$protocol://$userInfo${connection.host}:${connection.port}$fullPath"),
        )
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getMimeType(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "mpeg", "mpg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "ts" -> "video/mp2t"
      else -> null
    }
  }

  /** Internal representation of a WebDAV resource parsed from a PROPFIND response. */
  private data class WebDavEntry(
    val href: String,
    val displayName: String?,
    val isDirectory: Boolean,
    val contentLength: Long?,
    val contentType: String?,
    val lastModified: String?,
  )
}

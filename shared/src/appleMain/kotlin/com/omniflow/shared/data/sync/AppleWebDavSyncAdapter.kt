package com.omniflow.shared.data.sync

import com.omniflow.shared.domain.model.BackupRecord
import com.omniflow.shared.domain.model.RemoteBackupMeta
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.setHTTPMethod
import platform.Foundation.setHTTPBody
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class AppleWebDavSyncAdapter : SyncAdapter {
    override suspend fun listBackups(): Result<List<RemoteBackupMeta>> = runCatching {
        ensureDirectory()
        val response = request("PROPFIND", directoryUrl(), PROPFIND_BODY.encodeToByteArray(), mapOf("Depth" to "1"))
        require(response.status == 207) { "WebDAV PROPFIND 失败：${response.status}" }
        HREF.findAll(response.body.decodeToString()).mapNotNull { match ->
            parseMeta(match.groupValues[1].substringAfterLast('/'))
        }.distinctBy(RemoteBackupMeta::backupId).toList()
    }

    override suspend fun uploadBackup(backup: BackupRecord): Result<Unit> = runCatching {
        ensureDirectory()
        val body = JsonObject(mapOf(
            "deviceId" to JsonPrimitive(backup.deviceId),
            "backupId" to JsonPrimitive(backup.backupId),
            "createdAt" to JsonPrimitive(backup.createdAt.toEpochMilliseconds()),
            "payload" to JsonPrimitive(backup.payload),
        )).toString().encodeToByteArray()
        val response = request("PUT", fileUrl(backup.createdAt, backup.deviceId, backup.backupId), body)
        require(response.status in setOf(200, 201, 204)) { "WebDAV PUT 失败：${response.status}" }
    }

    override suspend fun downloadBackup(meta: RemoteBackupMeta): Result<BackupRecord> = runCatching {
        val response = request("GET", fileUrl(meta.createdAt, meta.deviceId, meta.backupId))
        require(response.status == 200) { "WebDAV GET 失败：${response.status}" }
        val json = Json.parseToJsonElement(response.body.decodeToString()).jsonObject
        BackupRecord(
            json.getValue("deviceId").jsonPrimitive.content,
            json.getValue("backupId").jsonPrimitive.content,
            Instant.fromEpochMilliseconds(json.getValue("createdAt").jsonPrimitive.content.toLong()),
            json.getValue("payload").jsonPrimitive.content,
        )
    }

    override suspend fun deleteBackup(meta: RemoteBackupMeta): Result<Unit> = runCatching {
        val response = request("DELETE", fileUrl(meta.createdAt, meta.deviceId, meta.backupId))
        require(response.status in setOf(200, 204, 404)) { "WebDAV DELETE 失败：${response.status}" }
    }

    private suspend fun ensureDirectory() {
        val response = request("MKCOL", directoryUrl())
        require(response.status in setOf(201, 405)) { "WebDAV MKCOL 失败：${response.status}" }
    }

    private suspend fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse = suspendCancellableCoroutine { continuation ->
        val request = NSMutableURLRequest().apply {
            setURL(NSURL.URLWithString(url) ?: error("WebDAV URL 无效"))
            setHTTPMethod(HTTPMethod = method)
            body?.let { setHTTPBody(HTTPBody = it.toData()) }
            setValue("Basic ${credentials()}", forHTTPHeaderField = "Authorization")
            if (body != null) setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
            headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                continuation.resumeWith(Result.failure(IllegalStateException(error.localizedDescription())))
            } else {
                val responseBody = if (data == null) byteArrayOf() else data.toKotlinByteArray()
                continuation.resume(
                    HttpResponse((response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0, responseBody),
                )
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    private fun endpoint(): String = NSUserDefaults.standardUserDefaults.stringForKey(ENDPOINT_KEY)
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf(String::isNotEmpty)
        ?.also { require(it.startsWith("https://")) { "WebDAV 地址必须使用 HTTPS" } }
        ?: error("请先填写 WebDAV 服务器目录")

    private fun directoryUrl() = "${endpoint()}/backups/"
    private fun fileUrl(createdAt: Instant, deviceId: String, backupId: String) =
        "${directoryUrl()}${createdAt.toEpochMilliseconds()}_${deviceId}_${backupId}.backup"

    private fun credentials(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val raw = "${defaults.stringForKey(USERNAME_KEY).orEmpty()}:${AppleWebDavCredentials.password}"
        return raw.encodeToByteArray().toData().base64EncodedStringWithOptions(0u)
    }

    private fun parseMeta(name: String): RemoteBackupMeta? {
        if (!name.endsWith(".backup")) return null
        val parts = name.removeSuffix(".backup").split('_', limit = 3)
        if (parts.size != 3) return null
        return RemoteBackupMeta(
            parts[1], parts[2], parts[0].toLongOrNull()?.let(Instant::fromEpochMilliseconds) ?: return null,
        )
    }

    private fun ByteArray.toData(): NSData = usePinned { NSData.create(it.addressOf(0), size.toULong()) }
    private fun NSData.toKotlinByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }

    private data class HttpResponse(val status: Int, val body: ByteArray)

    private companion object {
        const val ENDPOINT_KEY = "webdav.endpoint"
        const val USERNAME_KEY = "webdav.username"
        const val PROPFIND_BODY = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><getlastmodified/></prop></propfind>"""
        val HREF = Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.IGNORE_CASE)
    }
}

object AppleWebDavCredentials {
    internal var password: String = ""

    fun configure(endpoint: String, username: String, password: String) {
        NSUserDefaults.standardUserDefaults.apply {
            setObject(endpoint, forKey = "webdav.endpoint")
            setObject(username, forKey = "webdav.username")
        }
        this.password = password
    }
}

package com.omniflow.android

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.omniflow.shared.data.sync.SyncAdapter
import com.omniflow.shared.domain.model.BackupRecord
import com.omniflow.shared.domain.model.RemoteBackupMeta
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.json.JSONObject

class WebDavSyncAdapter(context: Context) : SyncAdapter {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("webdav", Context.MODE_PRIVATE)

    override suspend fun listBackups(): Result<List<RemoteBackupMeta>> = io {
        ensureDirectory()
        val connection = open(directoryUrl(), "PROPFIND").apply { setRequestProperty("Depth", "1") }
        connection.outputStream.use { it.write(PROPFIND_BODY.encodeToByteArray()) }
        requireStatus(connection, setOf(207))
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        HREF.findAll(response).mapNotNull { match ->
            val name = Uri.decode(match.groupValues[1]).substringAfterLast('/')
            parseMeta(name)
        }.distinctBy { it.backupId }.toList()
    }

    override suspend fun uploadBackup(backup: BackupRecord): Result<Unit> = io {
        ensureDirectory()
        val connection = open(fileUrl(fileName(backup.createdAt, backup.deviceId, backup.backupId)), "PUT")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val body = JSONObject()
            .put("deviceId", backup.deviceId)
            .put("backupId", backup.backupId)
            .put("createdAt", backup.createdAt.toEpochMilliseconds())
            .put("payload", backup.payload)
            .toString()
        connection.outputStream.use { it.write(body.encodeToByteArray()) }
        requireStatus(connection, setOf(200, 201, 204))
    }

    override suspend fun downloadBackup(meta: RemoteBackupMeta): Result<BackupRecord> = io {
        val connection = open(fileUrl(fileName(meta.createdAt, meta.deviceId, meta.backupId)), "GET")
        requireStatus(connection, setOf(200))
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        BackupRecord(
            deviceId = json.getString("deviceId"),
            backupId = json.getString("backupId"),
            createdAt = Instant.fromEpochMilliseconds(json.getLong("createdAt")),
            payload = json.getString("payload"),
        )
    }

    override suspend fun deleteBackup(meta: RemoteBackupMeta): Result<Unit> = io {
        val connection = open(fileUrl(fileName(meta.createdAt, meta.deviceId, meta.backupId)), "DELETE")
        requireStatus(connection, setOf(200, 204, 404))
    }

    private fun ensureDirectory() {
        val connection = open(directoryUrl(), "MKCOL")
        requireStatus(connection, setOf(201, 405))
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val username = preferences.getString("username", "").orEmpty()
        val password = WebDavCredentials.password(context)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
            doOutput = method in setOf("PUT", "PROPFIND")
            if (username.isNotEmpty() || password.isNotEmpty()) {
                val credentials = Base64.encodeToString("$username:$password".encodeToByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $credentials")
            }
        }
    }

    private fun requireStatus(connection: HttpURLConnection, expected: Set<Int>) {
        if (connection.responseCode in expected) return
        val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        error("WebDAV ${connection.requestMethod} 失败：${connection.responseCode} ${detail.take(160)}")
    }

    private fun endpoint(): String = preferences.getString("endpoint", "")
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf(String::isNotEmpty)
        ?.also { require(it.startsWith("https://")) { "WebDAV 地址必须使用 HTTPS" } }
        ?: error("请先填写 WebDAV 服务器目录")

    private fun directoryUrl(): String = "${endpoint()}/backups/"
    private fun fileUrl(name: String): String = directoryUrl() + Uri.encode(name)
    private fun fileName(createdAt: Instant, deviceId: String, backupId: String) =
        "${createdAt.toEpochMilliseconds()}_${deviceId}_${backupId}.backup"

    private fun parseMeta(name: String): RemoteBackupMeta? {
        if (!name.endsWith(".backup")) return null
        val parts = name.removeSuffix(".backup").split('_', limit = 3)
        if (parts.size != 3) return null
        return RemoteBackupMeta(
            deviceId = parts[1],
            backupId = parts[2],
            createdAt = parts[0].toLongOrNull()?.let(Instant::fromEpochMilliseconds) ?: return null,
        )
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }

    private companion object {
        val HREF = Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.IGNORE_CASE)
        const val PROPFIND_BODY = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><getlastmodified/></prop></propfind>"""
    }
}

object WebDavCredentials {
    private const val ALIAS = "omniflow-webdav"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, endpoint: String, username: String, password: String) {
        context.getSharedPreferences("webdav", Context.MODE_PRIVATE).edit()
            .putString("endpoint", endpoint.trimEnd('/'))
            .putString("username", username)
            .putString("encrypted_password", encrypt(password))
            .remove("password")
            .apply()
    }

    fun password(context: Context): String {
        val preferences = context.getSharedPreferences("webdav", Context.MODE_PRIVATE)
        preferences.getString("password", null)?.let { legacy ->
            save(
                context,
                preferences.getString("endpoint", "").orEmpty(),
                preferences.getString("username", "").orEmpty(),
                legacy,
            )
            return legacy
        }
        return preferences.getString("encrypted_password", null)?.let(::decrypt).orEmpty()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(cipher.doFinal(value.encodeToByteArray()), Base64.NO_WRAP)}"
    }

    private fun decrypt(value: String): String = runCatching {
        if (value.isEmpty()) return@runCatching ""
        val (iv, payload) = value.split(':', limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)).decodeToString()
    }.getOrDefault("")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }
}

package com.radioinfo.app

import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.NdefMessage
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object NfcPayloadUtils {
    private const val APPLICATION_RECORD_TYPE = "android.com:pkg"
    private const val FILE_RECORD_TYPE = "radioinfo.app:file"
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    data class FilePayload(val uri: Uri, val mimeType: String)

    fun isSafeWebUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US)
        return scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo.isNullOrBlank()
    }

    fun isSafePackageName(value: String): Boolean = PACKAGE_NAME.matches(value.trim())

    fun createWebMessage(value: String): NdefMessage? {
        val normalized = value.trim()
        if (!isSafeWebUrl(normalized)) return null
        return NdefMessage(arrayOf(NdefRecord.createUri(normalized)))
    }

    fun createApplicationMessage(value: String): NdefMessage? {
        val packageName = value.trim()
        if (!isSafePackageName(packageName)) return null
        return NdefMessage(arrayOf(NdefRecord.createApplicationRecord(packageName)))
    }

    fun createFileMessage(uri: Uri, mimeType: String): NdefMessage {
        val payload = "$mimeType\n$uri".toByteArray(StandardCharsets.UTF_8)
        return NdefMessage(arrayOf(NdefRecord.createExternal("radioinfo.app", "file", payload)))
    }

    fun applicationPackage(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE ||
            String(record.type, StandardCharsets.US_ASCII) != APPLICATION_RECORD_TYPE
        ) return null
        val packageName = String(record.payload, StandardCharsets.UTF_8).trim()
        return packageName.takeIf(::isSafePackageName)
    }

    fun filePayload(record: NdefRecord): FilePayload? {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE ||
            String(record.type, StandardCharsets.US_ASCII) != FILE_RECORD_TYPE
        ) return null
        val parts = String(record.payload, StandardCharsets.UTF_8).split('\n', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return null
        val uri = runCatching { Uri.parse(parts[1]) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "content") return null
        return FilePayload(uri, parts[0].trim().ifBlank { "application/octet-stream" })
    }

    fun isApplicationRecord(record: NdefRecord): Boolean = applicationPackage(record) != null

    fun isFileRecord(record: NdefRecord): Boolean = filePayload(record) != null
}

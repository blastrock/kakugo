package org.kaqui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import org.jetbrains.anko.longToast

class UriResolver {
    companion object {
        const val TAG = "UriResolver"

        fun getFilePath(context: Context, auri: Uri): String? {
            var uri = auri
            var selection: String? = null
            var selectionArgs: Array<String>? = null
            if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(context, uri)) {
                if (isExternalStorageDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    return Environment.getExternalStorageDirectory().path + "/" + split[1]
                } else if (isDownloadsDocument(uri)) {
                    try {
                        val id = DocumentsContract.getDocumentId(uri)
                        uri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                    } catch (e: Exception) {
                        Log.e(TAG, "Couldn't find path of $auri", e)
                        context.longToast(context.getString(R.string.failed_to_load_resource, e.message))
                        return null
                    }
                } else if (isMediaDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("image" == type) {
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    selection = "_id=?"
                    selectionArgs = arrayOf(split[1])
                }
            }
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                try {
                    context.contentResolver
                            .query(uri, projection, selection, selectionArgs, null)
                            .use { cursor ->
                                val columnIndex = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                                if (cursor.moveToFirst()) {
                                    return cursor.getString(columnIndex)
                                }
                            }
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't find path of $auri", e)
                    context.longToast(context.getString(R.string.failed_to_load_resource, e.message))
                    return null
                }
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }
            return null
        }

        private fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        private fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }

        private fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.authority
        }
    }
}
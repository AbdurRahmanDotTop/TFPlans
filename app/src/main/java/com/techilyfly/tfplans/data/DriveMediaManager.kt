package com.techilyfly.tfplans.data

import android.accounts.Account
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI

class DrivePermissionDeniedException(message: String) : Exception(message)

data class DriveFolderIds(
    val rootId: String?,
    val imagesId: String?,
    val recordingsId: String?
)

class DriveMediaManager(
    private val context: Context, 
    private val auth: FirebaseAuth,
    private val preferencesRepository: UserPreferencesRepository
) {

    private fun getDriveService(): Drive? {
        val user = auth.currentUser ?: return null
        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }
        if (!isGoogleUser) {
            Log.e("DriveMediaManager", "User is not a Google user. Drive sync disabled.")
            return null
        }

        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleSignInAccount == null) {
            Log.e("DriveMediaManager", "Google Sign-In account not found. User needs to re-authenticate.")
            return null
        }

        if (!GoogleSignIn.hasPermissions(googleSignInAccount, Scope(DriveScopes.DRIVE_FILE))) {
            Log.e("DriveMediaManager", "Drive scope not granted. User needs to grant Drive permission.")
            throw DrivePermissionDeniedException("Google Drive permission not granted")
        }
        
        val sysAccount = googleSignInAccount.account
        if (sysAccount == null) {
            Log.e("DriveMediaManager", "System account missing from GoogleSignInAccount.")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = sysAccount

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("TFPlans").build()
    }

    private suspend fun getOrCreateFolder(drive: Drive, folderName: String, parentId: String? = null, knownId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            if (knownId != null) {
                try {
                    val file = drive.files().get(knownId).setFields("id, trashed").execute()
                    if (file != null && file.trashed != true) {
                        return@withContext file.id
                    }
                } catch (e: Exception) {
                    Log.w("DriveMediaManager", "Known folder $folderName ($knownId) not found or accessible, will recreate.")
                }
            }

            val parentQuery = if (parentId != null) "and '$parentId' in parents" else "and 'root' in parents"
            val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false $parentQuery"
            
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                return@withContext result.files[0].id
            }

            val folderMetadata = com.google.api.services.drive.model.File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) parents = listOf(parentId)
            }

            val folder = drive.files().create(folderMetadata).setFields("id").execute()
            return@withContext folder.id
        } catch (e: GoogleJsonResponseException) {
            val errorMsg = e.details?.message ?: e.message ?: "Unknown API error"
            Log.e("DriveMediaManager", "API Error creating folder $folderName: $errorMsg")
            if (e.statusCode == 403 || e.statusCode == 401) {
                throw DrivePermissionDeniedException("Google Drive permission denied or revoked.")
            }
            throw Exception("Google Drive API Error ($folderName): $errorMsg")
        } catch (e: java.net.UnknownHostException) {
            Log.e("DriveMediaManager", "Network error creating folder $folderName: ${e.message}")
            throw Exception("Network error: Unable to connect to Google Drive.")
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            val exceptionName = e.javaClass.simpleName
            Log.e("DriveMediaManager", "Error creating folder $folderName: [$exceptionName] $msg", e)
            // Capture the cause if it exists to get more details
            val causeMsg = e.cause?.message?.let { " Cause: $it" } ?: ""
            throw Exception("Error with folder '$folderName': [$exceptionName] $msg$causeMsg")
        }
    }

    suspend fun uploadMedia(localUriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext null
            
            // Check if it's a local file
            if (!localUriString.startsWith("file://") && !localUriString.startsWith("/")) {
                return@withContext null // Already a remote URL or unrecognized format
            }

            val file = if (localUriString.startsWith("file://")) {
                File(URI(localUriString))
            } else {
                File(localUriString)
            }

            if (!file.exists()) return@withContext null

            // Determine mime type
            val mimeType = if (file.name.endsWith(".mp3") || file.name.endsWith(".m4a") || file.name.endsWith(".3gp") || file.name.endsWith(".wav")) {
                "audio/mpeg"
            } else {
                "image/jpeg"
            }

            val knownRootId = preferencesRepository.driveFolderRootId.value
            val knownImagesId = preferencesRepository.driveFolderImagesId.value
            val knownRecordingsId = preferencesRepository.driveFolderRecordingsId.value

            val rootFolderId = getOrCreateFolder(drive, "TF Plans", null, knownRootId) ?: return@withContext null
            val isAudio = mimeType.startsWith("audio")
            val subFolderName = if (isAudio) "Recordings" else "Images"
            val knownSubId = if (isAudio) knownRecordingsId else knownImagesId
            val subFolderId = getOrCreateFolder(drive, subFolderName, rootFolderId, knownSubId) ?: return@withContext null

            // Update preferences if they changed
            preferencesRepository.setDriveFolderIds(
                rootId = rootFolderId,
                imagesId = if (isAudio) knownImagesId else subFolderId,
                recordingsId = if (isAudio) subFolderId else knownRecordingsId
            )

            // Create file metadata
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = file.name
                parents = listOf(subFolderId)
            }

            val mediaContent = FileContent(mimeType, file)

            // Upload
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            return@withContext uploadedFile.id
        } catch (e: DrivePermissionDeniedException) {
            throw e
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "Upload failed: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun downloadMedia(driveFileId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext false
            
            if (destFile.exists()) {
                if (destFile.length() > 0) return@withContext true
                else destFile.delete() // Corrupted empty file, delete and try downloading again
            }

            FileOutputStream(destFile).use { out ->
                drive.files().get(driveFileId)
                    .executeMediaAndDownloadTo(out)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "Download failed: ${e.message}", e)
            if (destFile.exists()) destFile.delete()
            return@withContext false
        }
    }

    suspend fun initializeFolders(): DriveFolderIds? = withContext(Dispatchers.IO) {
        try {
            val knownRootId = preferencesRepository.driveFolderRootId.value
            val knownImagesId = preferencesRepository.driveFolderImagesId.value
            val knownRecordingsId = preferencesRepository.driveFolderRecordingsId.value

            val drive = getDriveService() ?: throw Exception("Drive service not available. Check permissions.")
            val rootFolderId = getOrCreateFolder(drive, "TF Plans", null, knownRootId) ?: return@withContext null
            val imagesFolderId = getOrCreateFolder(drive, "Images", rootFolderId, knownImagesId)
            val recordingsFolderId = getOrCreateFolder(drive, "Recordings", rootFolderId, knownRecordingsId)
            
            val ids = DriveFolderIds(rootFolderId, imagesFolderId, recordingsFolderId)
            preferencesRepository.setDriveFolderIds(rootFolderId, imagesFolderId, recordingsFolderId)
            return@withContext ids
        } catch (e: DrivePermissionDeniedException) {
            throw e
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "Failed to initialize folders: ${e.message}")
            throw e
        }
    }

    suspend fun uploadMediaAndCache(localUriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveId = uploadMedia(localUriString) ?: return@withContext null
            val mediaDir = File(context.filesDir, "drive_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            
            val originalFile = if (localUriString.startsWith("file://")) File(URI(localUriString)) else File(localUriString)
            val driveFile = File(mediaDir, driveId)
            
            if (originalFile.exists() && originalFile.absolutePath != driveFile.absolutePath) {
                originalFile.copyTo(driveFile, overwrite = true)
            }
            return@withContext "file://${driveFile.absolutePath}"
        } catch (e: DrivePermissionDeniedException) {
            throw e
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "Upload and cache failed: ${e.message}")
            return@withContext null
        }
    }
    
    // Parses note content JSON, uploads any local files to Drive, 
    // and replaces the local URI with the Drive File ID prefix (e.g. drive://ID)
    suspend fun processNoteForUpload(contentJson: String): String {
        if (!contentJson.trimStart().startsWith("[")) return contentJson
        try {
            val jsonArray = org.json.JSONArray(contentJson)
            var changed = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("uri")) {
                    val uri = obj.getString("uri")
                    if (uri.startsWith("/") || uri.startsWith("file://")) {
                        val originalFile = if (uri.startsWith("file://")) File(URI(uri)) else File(uri)
                        val mediaDir = File(context.filesDir, "drive_media")
                        
                        // If it's already in drive_media, its name is the Drive ID. Skip upload.
                        if (originalFile.parentFile?.absolutePath == mediaDir.absolutePath) {
                            val driveId = originalFile.name
                            obj.put("uri", "drive://$driveId")
                            changed = true
                        } else {
                            val driveId = uploadMedia(uri)
                            if (driveId != null) {
                                // Copy to drive_media to prevent re-downloading later
                                try {
                                    if (!mediaDir.exists()) mediaDir.mkdirs()
                                    val driveFile = File(mediaDir, driveId)
                                    if (originalFile.exists() && originalFile.absolutePath != driveFile.absolutePath) {
                                        originalFile.copyTo(driveFile, overwrite = true)
                                    }
                                } catch (e: Exception) {
                                    Log.e("DriveMediaManager", "Error copying local file: ${e.message}")
                                }
            
                                obj.put("uri", "drive://$driveId")
                                changed = true
                            }
                        }
                    }
                }
            }
            if (changed) {
                // Prevent org.json from corrupting the JSON by escaping forward slashes
                return jsonArray.toString().replace("\\/", "/")
            }
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "JSON processing for upload failed: ${e.message}")
        }
        return contentJson
    }

    // Parses note content JSON, downloads any Drive files locally, 
    // and replaces the Drive File ID prefix with the local URI
    suspend fun processNoteForDownload(contentJson: String): String {
        if (!contentJson.trimStart().startsWith("[")) return contentJson
        try {
            val jsonArray = org.json.JSONArray(contentJson)
            var changed = false
            val mediaDir = File(context.filesDir, "drive_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("uri")) {
                    val uri = obj.getString("uri")
                    var driveIdToDownload: String? = null
                    
                    if (uri.startsWith("drive://")) {
                        driveIdToDownload = uri.substringAfter("drive://")
                    } else if (uri.contains("/drive_media/")) {
                        // RECOVERY: If a local URL was mistakenly uploaded to the cloud by an older app version,
                        // we can still recover the Drive ID from the file name.
                        driveIdToDownload = uri.substringAfterLast("/")
                    }
                    
                    if (driveIdToDownload != null) {
                        val localFile = File(mediaDir, driveIdToDownload)
                        val success = downloadMedia(driveIdToDownload, localFile)
                        if (success) {
                            obj.put("uri", "file://${localFile.absolutePath}")
                            changed = true
                        }
                    }
                }
            }
            if (changed) {
                // Prevent org.json from corrupting the JSON by escaping forward slashes
                return jsonArray.toString().replace("\\/", "/")
            }
        } catch (e: Exception) {
            Log.e("DriveMediaManager", "JSON processing for download failed: ${e.message}")
        }
        return contentJson
    }
}

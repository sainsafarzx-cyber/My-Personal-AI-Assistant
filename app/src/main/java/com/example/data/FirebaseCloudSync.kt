package com.example.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "FirebaseCloudSync"

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class FirestoreUserPreferences(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val assistantName: String = "LEON AI",
    val ownerName: String = "",
    val creatorTagline: String = "",
    val defaultLanguage: String = AssistantLanguage.HINDI.code,
    val alwaysReadyEnabled: Boolean = false,
    val memoryConsentGranted: Boolean = true,
    val voiceSpeed: Double = 1.0,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreMemoryItem(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val memoryKey: String = "",
    val memoryValue: String = "",
    val category: String = MemoryCategory.PREFERENCE.wireValue,
    val approved: Boolean = true,
    val isPermanent: Boolean = true,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreCreatorStamp(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val ownerId: String = "",
    val creatorName: String = "",
    val tagline: String = "",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
)

class FirebaseCloudSync(
    private val db: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
    private val repository: LeonRepository
) {
    constructor(context: Context, repository: LeonRepository) : this(
        db = resolveFirestore(context),
        auth = resolveAuth(),
        repository = repository
    )

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _state.update {
            it.copy(
                signedInUid = user?.uid,
                userEmail = user?.email,
                displayName = user?.displayName
            )
        }
        if (user != null) {
            syncScope.launch {
                syncAllWithCloud(user.uid)
            }
        }
    }

    init {
        auth?.addAuthStateListener(authListener)
        syncScope.launch {
            fetchPublicCreatorStamp()
            auth?.currentUser?.uid?.let { uid ->
                syncAllWithCloud(uid)
            }
        }
    }

    private fun requireUserId(): String =
        auth?.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to sync Forever Vault to Cloud.")

    /**
     * Fetches the canonical non-PII public creator_stamp document so ANY fresh app download
     * automatically receives the Owner/Creator's name set in Cloud.
     */
    suspend fun fetchPublicCreatorStamp(): Result<FirestoreCreatorStamp?> {
        val firestore = db ?: return Result.success(null)
        val path = "app_branding/creator_stamp"
        return try {
            val snapshot = firestore.collection("app_branding")
                .document("creator_stamp")
                .get()
                .await()
            if (!snapshot.exists()) {
                return Result.success(null)
            }
            val stamp = snapshot.toObject(
                FirestoreCreatorStamp::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (stamp != null && stamp.creatorName.isNotBlank()) {
                _state.update {
                    it.copy(
                        publishedCreatorName = stamp.creatorName,
                        publishedCreatorTagline = stamp.tagline,
                        publishedCreatorOwnerId = stamp.ownerId
                    )
                }
                // Apply to local owner identity automatically if not already customized locally
                val localPrefs = repository.getPreferencesOnce()
                if (localPrefs.ownerName.isBlank()) {
                    repository.setOwnerIdentity(
                        ownerName = stamp.creatorName,
                        creatorTagline = stamp.tagline,
                        syncCloud = false
                    )
                }
            }
            Result.success(stamp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun pushPreferencesToCloud(prefs: UserPreferences): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable."))
        return try {
            val uid = requireUserId()
            val path = "users/$uid"
            val docRef = firestore.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanOwner = prefs.ownerName.trim().take(60)
            val cleanTagline = prefs.creatorTagline.trim().take(160)

            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "assistantName" to prefs.assistantName.ifBlank { "LEON AI" }.take(30),
                        "ownerName" to cleanOwner,
                        "creatorTagline" to cleanTagline,
                        "defaultLanguage" to prefs.defaultLanguage,
                        "alwaysReadyEnabled" to prefs.alwaysReadyEnabled,
                        "memoryConsentGranted" to prefs.memoryConsentGranted,
                        "voiceSpeed" to prefs.voiceSpeed.coerceIn(0.5, 2.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "assistantName" to prefs.assistantName.ifBlank { "LEON AI" }.take(30),
                        "ownerName" to cleanOwner,
                        "creatorTagline" to cleanTagline,
                        "defaultLanguage" to prefs.defaultLanguage,
                        "alwaysReadyEnabled" to prefs.alwaysReadyEnabled,
                        "memoryConsentGranted" to prefs.memoryConsentGranted,
                        "voiceSpeed" to prefs.voiceSpeed.coerceIn(0.5, 2.0),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth?.currentUser?.uid ?: "unauth"}")
            Result.failure(e)
        }
    }

    suspend fun pushMemoryToCloud(item: MemoryItem): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable."))
        return try {
            val uid = requireUserId()
            val docId = item.cloudDocId.ifBlank { stableMemoryDocId(item.memoryKey) }
            val path = "users/$uid/memories/$docId"
            val docRef = firestore.collection("users").document(uid)
                .collection("memories").document(docId)
            val existingSnap = docRef.get().await()

            val cleanKey = item.memoryKey.trim().take(80).ifBlank { "Memory" }
            val cleanVal = item.memoryValue.trim().take(500).ifBlank { cleanKey }
            val cleanCat = MemoryCategory.fromWire(item.category).wireValue

            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "memoryKey" to cleanKey,
                        "memoryValue" to cleanVal,
                        "category" to cleanCat,
                        "approved" to item.approved,
                        "isPermanent" to item.isPermanent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "memoryKey" to cleanKey,
                        "memoryValue" to cleanVal,
                        "category" to cleanCat,
                        "approved" to item.approved,
                        "isPermanent" to item.isPermanent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth?.currentUser?.uid ?: "unauth"}/memories")
            Result.failure(e)
        }
    }

    suspend fun deleteMemoryFromCloud(cloudDocId: String): Result<Unit> {
        if (cloudDocId.isBlank()) return Result.success(Unit)
        val firestore = db ?: return Result.success(Unit)
        val uid = auth?.currentUser?.uid ?: return Result.success(Unit)
        val path = "users/$uid/memories/$cloudDocId"
        return try {
            firestore.collection("users").document(uid)
                .collection("memories").document(cloudDocId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun publishCreatorStampGlobally(creatorName: String, tagline: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable."))
        val cleanName = creatorName.trim().take(60)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Creator name cannot be empty."))
        }
        val cleanTagline = tagline.trim().take(160)
        return try {
            val uid = requireUserId()
            val docRef = firestore.collection("app_branding").document("creator_stamp")
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existingOwner = snap.getString("ownerId").orEmpty()
                if (existingOwner.isNotBlank() && existingOwner != uid) {
                    return Result.failure(
                        IllegalStateException("Global Creator Stamp is already locked to the original app owner.")
                    )
                }
                docRef.update(
                    mapOf(
                        "creatorName" to cleanName,
                        "tagline" to cleanTagline,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerId" to uid,
                        "creatorName" to cleanName,
                        "tagline" to cleanTagline,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            _state.update {
                it.copy(
                    publishedCreatorName = cleanName,
                    publishedCreatorTagline = cleanTagline,
                    publishedCreatorOwnerId = uid,
                    statusMessage = "Global Creator Identity published: $cleanName",
                    errorMessage = null
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_branding/creator_stamp")
            _state.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to publish creator stamp.") }
            Result.failure(e)
        }
    }

    suspend fun syncAllWithCloud(uidOverride: String? = null): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable."))
        val uid = uidOverride ?: auth?.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in with Google first to sync Forever Vault."))

        _state.update { it.copy(isSyncing = true, errorMessage = null) }
        return try {
            // 1. Pull preferences & restore owner identity if local is blank
            val userSnap = firestore.collection("users").document(uid).get().await()
            val cloudPrefs = userSnap.toObject(
                FirestoreUserPreferences::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val localPrefs = repository.getPreferencesOnce()
            if (cloudPrefs != null) {
                val mergedOwner = localPrefs.ownerName.ifBlank { cloudPrefs.ownerName }
                val mergedTagline = localPrefs.creatorTagline.ifBlank { cloudPrefs.creatorTagline }
                if (mergedOwner != localPrefs.ownerName || mergedTagline != localPrefs.creatorTagline) {
                    repository.setOwnerIdentity(mergedOwner, mergedTagline, syncCloud = false)
                }
            }

            // 2. Pull cloud memories and merge into local Room DB
            val memSnaps = firestore.collection("users").document(uid)
                .collection("memories")
                .get()
                .await()
            val cloudMemories = memSnaps.toObjects(
                FirestoreMemoryItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            for (cm in cloudMemories) {
                repository.mergeCloudMemory(
                    cloudDocId = cm.id,
                    key = cm.memoryKey,
                    value = cm.memoryValue,
                    category = MemoryCategory.fromWire(cm.category),
                    isPermanent = cm.isPermanent
                )
            }

            // 3. Push latest merged local preferences & local memories back up to Cloud
            val refreshedPrefs = repository.getPreferencesOnce()
            pushPreferencesToCloud(refreshedPrefs)

            val localMemories = repository.getMemoriesOnce()
            for (lm in localMemories) {
                pushMemoryToCloud(lm)
            }

            // 4. Refresh public creator stamp status
            fetchPublicCreatorStamp()

            _state.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    statusMessage = "Forever Vault synced (${localMemories.size} memories)",
                    errorMessage = null
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/memories")
            _state.update {
                it.copy(
                    isSyncing = false,
                    errorMessage = e.localizedMessage ?: "Cloud sync failed."
                )
            }
            Result.failure(e)
        }
    }

    fun release() {
        auth?.removeAuthStateListener(authListener)
    }

    companion object {
        private fun resolveFirestore(context: Context): FirebaseFirestore? = runCatching {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        }.getOrNull()

        private fun resolveAuth(): FirebaseAuth? = runCatching {
            Firebase.auth
        }.getOrNull()

        fun stableMemoryDocId(key: String): String {
            val normalized = key.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val suffix = (key.trim().lowercase().hashCode() and 0x7fffffff).toString(36)
            val prefix = normalized.take(40).ifBlank { "fact" }
            return "mem_${prefix}_$suffix".take(120)
        }
    }
}

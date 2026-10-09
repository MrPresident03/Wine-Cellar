package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Date

/** One bottle waiting to be written by the importer. */
data class ImportItem(val draft: BottleDraft, val photo: PhotoData?, val createdAt: Long)

/**
 * All cloud data lives in Firestore, which also keeps an offline copy on the phone.
 * Layout:
 *   users/{uid}                         -> { email, cellarId }
 *   cellars/{cellarId}                  -> { name, ownerUid, members[], memberEmails[], sensorKey, alertThreshold }
 *   cellars/{cellarId}/bottles/{id}     -> one document per bottle (id is random, never the slot)
 *   cellars/{cellarId}/photos/{id}      -> { data } full-size photo for bottle {id}
 *   cellars/{cellarId}/climate/{auto}   -> { temperature, humidity, ts, sensorKey } written by the ESP
 *   invites/{code}                      -> { cellarId, createdBy, expiresAt }
 */
class CellarRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun userRef(uid: String) = db.collection("users").document(uid)
    private fun cellarRef(cellarId: String) = db.collection("cellars").document(cellarId)
    private fun bottlesRef(cellarId: String) = cellarRef(cellarId).collection("bottles")
    private fun photosRef(cellarId: String) = cellarRef(cellarId).collection("photos")
    private fun climateRef(cellarId: String) = cellarRef(cellarId).collection("climate")

    // ---------------------------------------------------------------- observing

    fun observeUserCellar(uid: String): Flow<UserCellarState> = callbackFlow {
        // INCLUDE metadata changes so we can tell "not in the offline cache yet" apart from
        // "the server says this user has no cellar". Without this a returning user could be
        // shown the create-a-cellar screen for a moment.
        val reg = userRef(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                Log.w(TAG, "users listen failed", err)
                trySend(UserCellarState.Error(friendlyFirestoreError(err)))
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            val cellarId = snap.getString("cellarId")
            val state = when {
                !cellarId.isNullOrBlank() -> UserCellarState.Ready(cellarId)
                snap.metadata.isFromCache -> UserCellarState.Loading
                else -> UserCellarState.None
            }
            trySend(state)
        }
        awaitClose { reg.remove() }
    }

    fun observeCellar(cellarId: String): Flow<Cellar?> = callbackFlow {
        val reg = cellarRef(cellarId).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "cellar listen failed", err)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) trySend(snap.toCellar())
        }
        awaitClose { reg.remove() }
    }

    fun observeBottles(cellarId: String, onError: (Exception) -> Unit): Flow<List<WineBottle>> = callbackFlow {
        val reg = bottlesRef(cellarId).addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            if (snap != null) trySend(snap.documents.mapNotNull { it.toBottle() })
        }
        awaitClose { reg.remove() }
    }

    fun observeLatestReading(cellarId: String): Flow<ClimateReading?> = callbackFlow {
        val reg = climateRef(cellarId)
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "latest reading listen failed", err)
                    return@addSnapshotListener
                }
                if (snap != null) trySend(snap.documents.firstOrNull()?.toReading())
            }
        awaitClose { reg.remove() }
    }

    fun observeHistory(cellarId: String, sinceMillis: Long): Flow<List<ClimateReading>> = callbackFlow {
        val reg = climateRef(cellarId)
            .whereGreaterThanOrEqualTo("ts", Timestamp(Date(sinceMillis)))
            .orderBy("ts", Query.Direction.ASCENDING)
            .limit(5000)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "history listen failed", err)
                    return@addSnapshotListener
                }
                if (snap != null) trySend(snap.documents.mapNotNull { it.toReading() })
            }
        awaitClose { reg.remove() }
    }

    // ---------------------------------------------------------------- cellar setup & sharing

    suspend fun createCellar(uid: String, email: String, name: String): String {
        val ref = db.collection("cellars").document()
        val batch = db.batch()
        batch.set(
            ref,
            hashMapOf(
                "name" to name,
                "ownerUid" to uid,
                "members" to listOf(uid),
                "memberEmails" to listOf(email),
                "sensorKey" to randomString(24, KEY_ALPHABET),
                "alertThreshold" to 18.0,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(userRef(uid), hashMapOf("email" to email, "cellarId" to ref.id), SetOptions.merge())
        batch.commit().await()
        return ref.id
    }

    suspend fun createInvite(uid: String, cellarId: String): String {
        var lastError: Exception? = null
        repeat(3) {
            val code = randomString(6, CODE_ALPHABET)
            try {
                db.collection("invites").document(code).set(
                    hashMapOf(
                        "cellarId" to cellarId,
                        "createdBy" to uid,
                        "expiresAt" to Timestamp(Date(System.currentTimeMillis() + INVITE_LIFETIME_MS))
                    )
                ).await()
                return code
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Couldn't create an invite code.")
    }

    suspend fun joinCellar(uid: String, email: String, rawCode: String): String {
        val code = rawCode.uppercase().filter { it.isLetterOrDigit() }
        if (code.length < 4) throw IllegalArgumentException("Enter the invite code you were sent.")
        val invite = db.collection("invites").document(code).get().await()
        if (!invite.exists()) throw IllegalArgumentException("That invite code doesn't exist. Check it and try again.")
        val cellarId = invite.getString("cellarId")
            ?: throw IllegalArgumentException("That invite code is invalid.")
        val expires = invite.getTimestamp("expiresAt")
        if (expires != null && expires.toDate().time < System.currentTimeMillis()) {
            throw IllegalArgumentException("That invite code has expired. Ask for a new one.")
        }
        cellarRef(cellarId).set(
            hashMapOf(
                "members" to FieldValue.arrayUnion(uid),
                "memberEmails" to FieldValue.arrayUnion(email),
                "lastJoinCode" to code
            ),
            SetOptions.merge()
        ).await()
        userRef(uid).set(hashMapOf("email" to email, "cellarId" to cellarId), SetOptions.merge()).await()
        return cellarId
    }

    fun updateCellarFields(cellarId: String, fields: Map<String, Any>, onError: (Exception) -> Unit) {
        cellarRef(cellarId).set(fields, SetOptions.merge())
            .addOnFailureListener { e -> onError(e) }
    }

    // ---------------------------------------------------------------- bottles

    private fun draftFields(d: BottleDraft, uid: String): HashMap<String, Any?> = hashMapOf(
        "wineryName" to d.wineryName,
        "classification" to d.classification,
        "varietal" to d.varietal,
        "vintage" to d.vintage,
        "gridRow" to d.gridRow,
        "gridCol" to d.gridCol,
        "price" to d.price,
        "isAging" to d.isAging,
        "preset" to d.preset,
        "updatedAt" to FieldValue.serverTimestamp(),
        "updatedBy" to uid
    )

    private fun photoFields(p: PhotoData): HashMap<String, Any> = hashMapOf(
        "data" to p.full,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    /** Writes go to the offline cache instantly and sync when there's signal, so we don't wait on them. */
    fun addBottle(cellarId: String, uid: String, draft: BottleDraft, photo: PhotoData?, onError: (Exception) -> Unit) {
        val ref = bottlesRef(cellarId).document()
        val data = draftFields(draft, uid)
        data["createdAt"] = System.currentTimeMillis()
        data["thumb"] = photo?.thumb
        data["hasPhoto"] = photo != null
        val batch = db.batch()
        batch.set(ref, data)
        if (photo != null) batch.set(photosRef(cellarId).document(ref.id), photoFields(photo))
        batch.commit().addOnFailureListener { e -> onError(e) }
    }

    fun updateBottle(
        cellarId: String,
        uid: String,
        bottleId: String,
        draft: BottleDraft,
        change: PhotoChange,
        onError: (Exception) -> Unit
    ) {
        val ref = bottlesRef(cellarId).document(bottleId)
        val data = draftFields(draft, uid)
        val batch = db.batch()
        when (change) {
            is PhotoChange.Keep -> Unit
            is PhotoChange.Remove -> {
                data["thumb"] = null
                data["hasPhoto"] = false
                batch.delete(photosRef(cellarId).document(bottleId))
            }
            is PhotoChange.Replace -> {
                data["thumb"] = change.photo.thumb
                data["hasPhoto"] = true
                batch.set(photosRef(cellarId).document(bottleId), photoFields(change.photo))
            }
        }
        batch.set(ref, data, SetOptions.merge())
        batch.commit().addOnFailureListener { e -> onError(e) }
    }

    /** Moves [bottle] to row/col. If [occupant] is there, the two swap places in one atomic write. */
    fun moveBottle(
        cellarId: String,
        uid: String,
        bottle: WineBottle,
        row: Int,
        col: Int,
        occupant: WineBottle?,
        onError: (Exception) -> Unit
    ) {
        val batch = db.batch()
        batch.set(
            bottlesRef(cellarId).document(bottle.id),
            hashMapOf("gridRow" to row, "gridCol" to col, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid),
            SetOptions.merge()
        )
        if (occupant != null && occupant.id != bottle.id) {
            batch.set(
                bottlesRef(cellarId).document(occupant.id),
                hashMapOf(
                    "gridRow" to bottle.gridRow,
                    "gridCol" to bottle.gridCol,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid
                ),
                SetOptions.merge()
            )
        }
        batch.commit().addOnFailureListener { e -> onError(e) }
    }

    /** Copies a bottle (including its photo) into a new slot. */
    suspend fun duplicateBottle(cellarId: String, uid: String, bottle: WineBottle, row: Int, col: Int, onError: (Exception) -> Unit) {
        val full = if (bottle.hasPhoto) {
            try {
                getPhoto(cellarId, bottle.id)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val thumb = bottle.thumb
        val photo = if (full != null && thumb != null) PhotoData(thumb, full) else null
        val draft = BottleDraft(
            wineryName = bottle.wineryName,
            classification = bottle.classification,
            varietal = bottle.varietal,
            vintage = bottle.vintage,
            gridRow = row,
            gridCol = col,
            price = bottle.price,
            isAging = bottle.isAging,
            preset = bottle.preset ?: presetForVarietal(bottle.varietal)
        )
        addBottle(cellarId, uid, draft, photo, onError)
    }

    fun deleteBottle(cellarId: String, bottleId: String, onError: (Exception) -> Unit) {
        val batch = db.batch()
        batch.delete(bottlesRef(cellarId).document(bottleId))
        batch.delete(photosRef(cellarId).document(bottleId))
        batch.commit().addOnFailureListener { e -> onError(e) }
    }

    suspend fun getPhoto(cellarId: String, bottleId: String): String? =
        photosRef(cellarId).document(bottleId).get().await().getString("data")

    suspend fun bottlesOnce(cellarId: String): List<WineBottle> =
        bottlesRef(cellarId).get().await().documents.mapNotNull { it.toBottle() }

    // ---------------------------------------------------------------- import from version 1

    /** Reads the old per-email cloud copy (cellars/cellar_<name>/bottles). Returns empty if unreadable. */
    suspend fun legacyCloudBottles(legacyCellarId: String): List<DocumentSnapshot> = try {
        withTimeoutOrNull(15_000) {
            db.collection("cellars").document(legacyCellarId).collection("bottles").get().await().documents
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read old cloud data for $legacyCellarId", e)
        emptyList()
    }

    /**
     * Writes imported bottles in batches. Returns false if some batches were still waiting for the
     * server after the timeout (they stay queued on the phone and upload automatically later).
     */
    suspend fun writeImported(cellarId: String, uid: String, items: List<ImportItem>): Boolean {
        var allConfirmed = true
        var batch = db.batch()
        var ops = 0
        var bytes = 0L

        suspend fun flush() {
            if (ops == 0) return
            val toCommit = batch
            val confirmed = withTimeoutOrNull(30_000) {
                toCommit.commit().await()
                true
            } ?: false
            if (!confirmed) allConfirmed = false
            batch = db.batch()
            ops = 0
            bytes = 0L
        }

        for (item in items) {
            val ref = bottlesRef(cellarId).document()
            val data = draftFields(item.draft, uid)
            data["createdAt"] = item.createdAt
            data["thumb"] = item.photo?.thumb
            data["hasPhoto"] = item.photo != null
            data["importedFromV1"] = true
            batch.set(ref, data)
            ops++
            bytes += 1_000L + (item.photo?.thumb?.length ?: 0)
            val photo = item.photo
            if (photo != null) {
                batch.set(photosRef(cellarId).document(ref.id), photoFields(photo))
                ops++
                bytes += photo.full.length
            }
            if (ops >= 400 || bytes >= 4_000_000L) flush()
        }
        flush()
        return allConfirmed
    }

    // ---------------------------------------------------------------- mapping

    private fun DocumentSnapshot.toBottle(): WineBottle? = try {
        WineBottle(
            id = id,
            wineryName = getString("wineryName") ?: "",
            classification = getString("classification"),
            varietal = getString("varietal") ?: "",
            vintage = getString("vintage") ?: "",
            gridRow = getLong("gridRow")?.toInt() ?: 1,
            gridCol = getLong("gridCol")?.toInt() ?: 1,
            price = getDouble("price"),
            isAging = getBoolean("isAging") ?: false,
            preset = getString("preset"),
            thumb = getString("thumb"),
            hasPhoto = getBoolean("hasPhoto") ?: false,
            createdAt = getLong("createdAt") ?: 0L
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping unreadable bottle $id", e)
        null
    }

    private fun DocumentSnapshot.toCellar(): Cellar = Cellar(
        id = id,
        name = getString("name") ?: "My Cellar",
        ownerUid = getString("ownerUid") ?: "",
        members = (get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        memberEmails = (get("memberEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        sensorKey = getString("sensorKey") ?: "",
        alertThreshold = getDouble("alertThreshold") ?: 18.0
    )

    private fun DocumentSnapshot.toReading(): ClimateReading? {
        val ts = getTimestamp("ts") ?: return null
        val temperature = getDouble("temperature") ?: return null
        val humidity = getDouble("humidity") ?: return null
        return ClimateReading(ts.toDate().time, temperature.toFloat(), humidity.toFloat())
    }

    companion object {
        private const val TAG = "CellarRepository"
        private const val INVITE_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val KEY_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun randomString(length: Int, alphabet: String): String =
            (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")

        fun friendlyFirestoreError(e: Throwable): String {
            if (e is FirebaseFirestoreException) {
                return when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission denied by the cloud database. Check the new Firestore rules have been published (see README)."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Can't reach the cloud right now. Changes are saved on this phone and will sync later."
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Please sign in again."
                    else -> e.localizedMessage ?: "Cloud error (${e.code})."
                }
            }
            return e.localizedMessage ?: "Something went wrong."
        }
    }
}

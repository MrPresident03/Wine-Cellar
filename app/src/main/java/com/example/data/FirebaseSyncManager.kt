package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(
    private val context: Context,
    private val repository: WineCellarRepository,
    private val scope: CoroutineScope
) {
    private val sharedPrefs = context.getSharedPreferences("cellar_sync_prefs", Context.MODE_PRIVATE)

    private val _cellarId = MutableStateFlow("")
    val cellarId: StateFlow<String> = _cellarId.asStateFlow()

    private val _syncStatus = MutableStateFlow("Offline")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private var firestoreRegistration: ListenerRegistration? = null
    private var isSyncingInProgress = false

    private val defaultApiKey = "AIzaSyATKV0t7n2uJCDZaq0SjRYATqz8XHLP17c"
    private val defaultProjectId = "wine-cellar-app-c1107"

    init {
        val email = _userEmail.value
        if (email.isNotBlank()) {
            setupUser(email)
        }
    }

    fun setupUser(email: String) {
        val cleanEmail = email.trim()
        _userEmail.value = cleanEmail
        sharedPrefs.edit().putString("user_email", cleanEmail).apply()

        if (cleanEmail.isNotBlank()) {
            // Mapped to cellar_<username> so that entering the same email on any device links instantly to the same node
            val computedId = "cellar_" + cleanEmail.split("@")[0].replace(".", "_")
            _cellarId.value = computedId
            
            scope.launch(Dispatchers.IO) {
                _syncStatus.value = "Connecting..."
                val ok = initFirebase()
                if (ok) {
                    startRealtimeSync()
                } else {
                    _syncStatus.value = "Init Failed"
                }
            }
        } else {
            stopRealtimeSync()
            _cellarId.value = ""
            _syncStatus.value = "Signed Out"
        }
    }

    fun setUserEmail(email: String) {
        setupUser(email)
    }

    // Unused properties and backward-compatibility helper methods to prevent compilation breakage elsewhere 
    val isFirebaseEnabled = MutableStateFlow(true)
    val pairingCode = MutableStateFlow("")
    fun enableFirebase(enabled: Boolean) {}
    fun generatePairingCode(): String = ""
    fun joinCellarByPairingCode(code: String): Boolean = false
    fun setCellarId(id: String) {}

    fun logout() {
        setUserEmail("")
    }

    private fun initFirebase(): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            if (apps.any { it.name == "[DEFAULT]" }) {
                return true
            }

            val options = FirebaseOptions.Builder()
                .setApiKey(defaultApiKey)
                .setApplicationId("1:969319706983:android:09f99a037fe1183f76ffc2")
                .setProjectId(defaultProjectId)
                .setStorageBucket("wine-cellar-app-c1107.firebasestorage.app")
                .build()

            FirebaseApp.initializeApp(context, options)
            Log.d("FirebaseSync", "Firebase initialized successfully")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Firebase init failure: ${e.message}", e)
            false
        }
    }

    fun startRealtimeSync() {
        val cid = _cellarId.value
        if (cid.isBlank()) return

        stopRealtimeSync()
        _syncStatus.value = "Syncing..."

        try {
            val db = FirebaseFirestore.getInstance()
            val collectionRef = db.collection("cellars").document(cid).collection("bottles")

            // Real-time listener from Firestore -> native sqlite Room
            firestoreRegistration = collectionRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("FirebaseSync", "Firestore Error: ${error.message}", error)
                    _syncStatus.value = "Error"
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    scope.launch(Dispatchers.IO) {
                        isSyncingInProgress = true
                        try {
                            for (doc in snapshots.documents) {
                                val row = doc.getLong("gridRow")?.toInt() ?: 1
                                val col = doc.getLong("gridCol")?.toInt() ?: 1
                                val winery = doc.getString("wineryName") ?: ""
                                val classification = doc.getString("classification")
                                val varietal = doc.getString("varietal") ?: ""
                                val vintage = doc.getString("vintage") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val photoUri = doc.getString("photoUri")
                                val price = doc.getDouble("price")
                                val isAging = doc.getBoolean("isAging") ?: false

                                repository.insertFromSync(
                                    row = row,
                                    col = col,
                                    winery = winery,
                                    classification = classification,
                                    varietal = varietal,
                                    vintage = vintage,
                                    timestamp = timestamp,
                                    photoUri = photoUri,
                                    price = price,
                                    isAging = isAging
                                )
                            }
                            _syncStatus.value = "Fully Synced"
                        } catch (e: Exception) {
                            Log.e("FirebaseSync", "Error writing update: ${e.message}", e)
                        } finally {
                            isSyncingInProgress = false
                        }
                    }
                } else if (snapshots != null && snapshots.isEmpty) {
                    triggerInitialUpload()
                }
            }

            // Real-time local SQLite -> Cloud Firestore
            scope.launch(Dispatchers.IO) {
                repository.allBottles.collect { localBottles ->
                    if (isSyncingInProgress) return@collect
                    val activeId = _cellarId.value
                    if (activeId.isBlank()) return@collect

                    try {
                        val remoteDocs = db.collection("cellars").document(activeId).collection("bottles").get().await()
                        val localDocIds = localBottles.map { "slot_${it.gridRow}_${it.gridCol}" }.toSet()

                        val batch = db.batch()
                        var totalOperations = 0

                        // 1. Delete documents from Firestore that do not exist locally anymore (synchronizing Deletes)
                        for (doc in remoteDocs.documents) {
                            if (doc.id !in localDocIds) {
                                batch.delete(doc.reference)
                                totalOperations++
                            }
                        }

                        // 2. Write/Update active local bottles to Firestore (synchronizing Insertions/Edits)
                        for (bottle in localBottles) {
                            val docId = "slot_${bottle.gridRow}_${bottle.gridCol}"
                            val docRef = db.collection("cellars").document(activeId).collection("bottles").document(docId)

                            val wineMap = hashMapOf(
                                "gridRow" to bottle.gridRow,
                                "gridCol" to bottle.gridCol,
                                "wineryName" to bottle.wineryName,
                                "classification" to bottle.classification,
                                "varietal" to bottle.varietal,
                                "vintage" to bottle.vintage,
                                "timestamp" to bottle.timestamp,
                                "photoUri" to bottle.photoUri,
                                "price" to bottle.price,
                                "isAging" to bottle.isAging
                            )

                            batch.set(docRef, wineMap, SetOptions.merge())
                            totalOperations++
                        }

                        if (totalOperations > 0) {
                            batch.commit().await()
                            Log.d("FirebaseSync", "Successfully synced $totalOperations changes to Firestore under ID: $activeId")
                            _syncStatus.value = "Fully Synced"
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseSync", "Push error: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("FirebaseSync", "Firestore realtime init error: ${e.message}", e)
            _syncStatus.value = "Init Mismatch"
        }
    }

    private fun triggerInitialUpload() {
        val cid = _cellarId.value
        if (cid.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                repository.pushAllToFirebase(db, cid)
                _syncStatus.value = "Fully Synced"
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Initial upload failed: ${e.message}", e)
            }
        }
    }

    fun stopRealtimeSync() {
        firestoreRegistration?.remove()
        firestoreRegistration = null
    }
}

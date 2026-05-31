package com.example.macromax

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin sync layer on top of SharedPreferences.
 * All writes are fire-and-forget (best-effort); the local cache is always
 * written first so the UI is never blocked waiting on the network.
 *
 * Firestore layout:
 *   users/{uid}/
 *     profile                         (single document)
 *     foodLog/{dateKey}               (one doc per day)
 *     workouts/{dateKey}              (one doc per day)
 *     water/{dateKey}                 (one doc per day)
 */
object FirestoreRepository {

    private val db  get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // ── Food log ──────────────────────────────────────────────────────────────

    /**
     * Read today's food log from SharedPreferences and push it to Firestore.
     * Call this after every local write to the food log.
     */
    fun syncFoodLog(dateKey: String, prefs: SharedPreferences) {
        val uid = uid ?: return
        val json    = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
        val arr     = JSONArray(json)
        val entries = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            mapOf(
                "name" to o.optString("name"),
                "cal"  to o.optInt("cal"),
                "pro"  to o.optInt("pro"),
                "fat"  to o.optInt("fat"),
                "car"  to o.optInt("car"),
                "meal" to o.optString("meal", "other")
            )
        }
        db.collection("users").document(uid)
            .collection("foodLog").document(dateKey)
            .set(mapOf("entries" to entries, "updatedAt" to FieldValue.serverTimestamp()))
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    fun syncWorkouts(dateKey: String, prefs: SharedPreferences) {
        val uid = uid ?: return
        val json    = prefs.getString("workout_log_$dateKey", "[]") ?: "[]"
        val arr     = JSONArray(json)
        val entries = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            mapOf(
                "id"       to o.optString("id"),
                "type"     to o.optString("type"),
                "name"     to o.optString("name"),
                "duration" to o.optInt("duration"),
                "calories" to o.optInt("calories")
            )
        }
        db.collection("users").document(uid)
            .collection("workouts").document(dateKey)
            .set(mapOf("entries" to entries, "updatedAt" to FieldValue.serverTimestamp()))
    }

    // ── Water ─────────────────────────────────────────────────────────────────

    fun syncWater(dateKey: String, glasses: Int) {
        val uid = uid ?: return
        db.collection("users").document(uid)
            .collection("water").document(dateKey)
            .set(mapOf("glasses" to glasses, "updatedAt" to FieldValue.serverTimestamp()))
    }

    // ── Profile / targets ─────────────────────────────────────────────────────

    /**
     * Push the user's nutrition targets and basic profile fields to Firestore.
     * Uses merge so only updated keys are overwritten.
     */
    fun syncProfile(prefs: SharedPreferences) {
        val uid = uid ?: return
        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val data: Map<String, Any> = mapOf(
            "targetCalories"  to prefs.getInt("target_calories",  0),
            "targetProteinG"  to prefs.getInt("target_protein_g", 0),
            "targetFatG"      to prefs.getInt("target_fat_g",     0),
            "targetCarbsG"    to prefs.getInt("target_carbs_g",   0),
            "name"            to (prefs.getString("user_name",      "") ?: ""),
            "email"           to email.trim().lowercase(),
            "weightValue"     to prefs.getInt("weight_value",     0),
            "weightUnit"      to (prefs.getString("weight_unit",    "kg") ?: "kg"),
            "heightCm"        to prefs.getInt("height_cm",        0),
            "age"             to prefs.getInt("user_age",         0),
            "gender"          to (prefs.getString("user_gender",    "") ?: ""),
            "goalType"        to (prefs.getString("goal_type",      "") ?: ""),
            "activityLevel"   to (prefs.getString("activity_level", "") ?: ""),
            "shareStats"      to prefs.getBoolean("share_stats", true),
            "updatedAt"       to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
    }

    // ── Pre-logout full sync ──────────────────────────────────────────────────

    /**
     * Push ALL local data to Firestore and wait for confirmation before the
     * caller signs out.  This is critical: once [FirebaseAuth.signOut] is
     * called the auth token is revoked, so any pending writes would fail.
     *
     * Syncs: full profile (including onboarding flag) + today's food log +
     * today's workouts.  Calls [onDone] after all three complete or fail.
     */
    fun syncBeforeLogout(
        prefs:   SharedPreferences,
        dateKey: String,
        onDone:  () -> Unit
    ) {
        val uid  = uid  ?: run { onDone(); return }
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email.orEmpty()

        var pending  = 3
        var finished = false   // guard against calling onDone more than once
        fun done() { if (!finished && --pending == 0) { finished = true; onDone() } }

        try {
            // 1. Full profile
            val name = sequenceOf(
                user?.displayName,
                prefs.getString("user_name", ""),
                email.substringBefore("@")
            ).firstOrNull { !it.isNullOrBlank() } ?: ""

            val profileData: Map<String, Any> = mapOf(
                "email"              to email.trim().lowercase(),
                "name"               to name,
                "targetCalories"     to prefs.getInt("target_calories",  0),
                "targetProteinG"     to prefs.getInt("target_protein_g", 0),
                "targetFatG"         to prefs.getInt("target_fat_g",     0),
                "targetCarbsG"       to prefs.getInt("target_carbs_g",   0),
                "weightValue"        to prefs.getInt("weight_value",     0),
                "weightUnit"         to (prefs.getString("weight_unit",    "kg") ?: "kg"),
                "heightCm"           to prefs.getInt("height_cm",        0),
                "age"                to prefs.getInt("user_age",         0),
                "gender"             to (prefs.getString("user_gender",    "") ?: ""),
                "goalType"           to (prefs.getString("goal_type",      "") ?: ""),
                "activityLevel"      to (prefs.getString("activity_level", "") ?: ""),
                "onboardingComplete" to prefs.getBoolean("onboarding_complete", false),
                "shareStats"         to prefs.getBoolean("share_stats",        true),
                "updatedAt"          to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .set(profileData, SetOptions.merge())
                .addOnCompleteListener { done() }

            // Keep emailIndex in sync on logout too
            if (email.isNotBlank()) {
                db.collection("emailIndex").document(email.trim().lowercase())
                    .set(mapOf("uid" to uid, "displayName" to name,
                               "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            }

            // 2. Today's food log
            val foodEntries = try {
                val arr = org.json.JSONArray(prefs.getString("food_log_$dateKey", "[]") ?: "[]")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    mapOf("name" to o.optString("name"), "cal" to o.optInt("cal"),
                          "pro"  to o.optInt("pro"),     "fat" to o.optInt("fat"),
                          "car"  to o.optInt("car"),     "meal" to o.optString("meal", "other"))
                }
            } catch (_: Exception) { emptyList() }

            db.collection("users").document(uid)
                .collection("foodLog").document(dateKey)
                .set(mapOf("entries" to foodEntries, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnCompleteListener { done() }

            // 3. Today's workouts
            val wEntries = try {
                val arr = org.json.JSONArray(prefs.getString("workout_log_$dateKey", "[]") ?: "[]")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    mapOf("id"       to o.optString("id"),       "type" to o.optString("type"),
                          "name"     to o.optString("name"),     "duration" to o.optInt("duration"),
                          "calories" to o.optInt("calories"))
                }
            } catch (_: Exception) { emptyList() }

            db.collection("users").document(uid)
                .collection("workouts").document(dateKey)
                .set(mapOf("entries" to wEntries, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnCompleteListener { done() }

        } catch (e: Exception) {
            // Something went wrong before all writes were started — call onDone
            // so the logout dialog never gets permanently stuck.
            if (!finished) { finished = true; onDone() }
        }
    }

    // ── User registration ─────────────────────────────────────────────────────

    /**
     * Write (or update) this user's email and display name to their Firestore
     * document so they are discoverable by the friend search.
     *
     * Must be called on every login and awaited before navigating away —
     * fire-and-forget is unreliable here because the activity may finish
     * before the write completes.
     *
     * Guest (anonymous) users have no email so the write is skipped and
     * [onDone] is called immediately.
     */
    /**
     * Write this user's email + display name to Firestore so they are
     * discoverable by friend search.
     *
     * Takes explicit parameters from the auth result — never reads from
     * FirebaseAuth.getInstance() so there's no race with auth state.
     *
     * Email is normalised to lowercase so queries are case-insensitive.
     * Fire-and-forget: Firestore offline persistence queues the write and
     * delivers it when connectivity is available.
     */
    /**
     * Index this user so they are findable by email in friend search.
     *
     * Writes to TWO places:
     *  1. users/{uid}            — merge email + name (existing data preserved)
     *  2. emailIndex/{email}     — document ID IS the email, value is their uid
     *
     * The emailIndex collection is looked up by document ID (a direct read),
     * not a collection query. Direct reads work regardless of security rules
     * that restrict cross-user collection queries.
     */
    fun registerUser(uid: String, email: String, displayName: String) {
        if (uid.isBlank() || email.isBlank()) return
        val normalizedEmail = email.trim().lowercase()
        val name = displayName.ifBlank { normalizedEmail.substringBefore("@") }

        // 1. User profile document
        db.collection("users").document(uid)
            .set(
                mapOf("email" to normalizedEmail, "name" to name,
                      "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )

        // 2. Email → UID lookup index (document ID = email address)
        db.collection("emailIndex").document(normalizedEmail)
            .set(
                mapOf("uid" to uid, "displayName" to name,
                      "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
    }

    // ── Pull on sign-in ───────────────────────────────────────────────────────

    /**
     * Read today's food log and workouts from Firestore and merge them into
     * SharedPreferences.  Calls [onDone] when the pull is complete (or fails).
     *
     * Strategy: Firestore wins if its document is newer (has more entries or
     * the local prefs are empty). This handles the case where a user logs in
     * on a new device.
     */
    fun pullTodayData(
        dateKey: String,
        prefs:   SharedPreferences,
        onDone:  () -> Unit
    ) {
        val uid = uid ?: run { onDone(); return }
        val userRef = db.collection("users").document(uid)

        // We do two gets; count down to zero then call onDone
        var pending = 2
        fun done() { if (--pending == 0) onDone() }

        // Food log
        userRef.collection("foodLog").document(dateKey).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val remote = doc.get("entries") as? List<Map<String, Any>> ?: emptyList()
                    val localJson  = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
                    val localCount = JSONArray(localJson).length()

                    // Only overwrite if remote has more entries (first login / new device)
                    if (remote.size > localCount) {
                        val arr = JSONArray()
                        remote.forEach { e ->
                            arr.put(JSONObject().apply {
                                put("name", e["name"] as? String ?: "")
                                put("cal",  (e["cal"]  as? Long)?.toInt() ?: 0)
                                put("pro",  (e["pro"]  as? Long)?.toInt() ?: 0)
                                put("fat",  (e["fat"]  as? Long)?.toInt() ?: 0)
                                put("car",  (e["car"]  as? Long)?.toInt() ?: 0)
                                put("meal", e["meal"] as? String ?: "other")
                            })
                        }
                        prefs.edit().putString("food_log_$dateKey", arr.toString()).apply()
                    }
                }
                done()
            }
            .addOnFailureListener { done() }

        // Workouts
        userRef.collection("workouts").document(dateKey).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val remote = doc.get("entries") as? List<Map<String, Any>> ?: emptyList()
                    val localJson  = prefs.getString("workout_log_$dateKey", "[]") ?: "[]"
                    val localCount = JSONArray(localJson).length()

                    if (remote.size > localCount) {
                        val arr = JSONArray()
                        remote.forEach { e ->
                            arr.put(JSONObject().apply {
                                put("id",       e["id"]       as? String ?: "")
                                put("type",     e["type"]     as? String ?: "other")
                                put("name",     e["name"]     as? String ?: "")
                                put("duration", (e["duration"] as? Long)?.toInt() ?: 0)
                                put("calories", (e["calories"] as? Long)?.toInt() ?: 0)
                            })
                        }
                        prefs.edit().putString("workout_log_$dateKey", arr.toString()).apply()
                    }
                }
                done()
            }
            .addOnFailureListener { done() }
    }

    // ── Friends ───────────────────────────────────────────────────────────────

    data class FriendRequest(val fromUid: String, val displayName: String, val email: String)

    data class FriendSummary(
        val uid:               String,
        val displayName:       String,
        val caloriesConsumed:  Int,
        val caloriesTarget:    Int,
        val caloriesBurned:    Int,
        val shareStats:        Boolean
    )

    /**
     * Look up a user by email and send them a friend request.
     * [onResult] receives (success, message).
     */
    fun sendFriendRequest(
        email:    String,
        myName:   String,
        onResult: (Boolean, String) -> Unit
    ) {
        val myUid           = uid ?: run { onResult(false, "Not signed in"); return }
        val normalizedEmail = email.trim().lowercase()

        // Look up target user via emailIndex — a DIRECT read by document ID,
        // not a collection query. This works regardless of security rules that
        // restrict cross-user queries on the users collection.
        db.collection("emailIndex").document(normalizedEmail).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(false, "No MacroMax account found for $normalizedEmail")
                    return@addOnSuccessListener
                }
                val theirUid = doc.getString("uid") ?: run {
                    onResult(false, "Invalid user data"); return@addOnSuccessListener
                }
                if (theirUid == myUid) {
                    onResult(false, "That's your own account")
                    return@addOnSuccessListener
                }
                // Check not already friends
                db.collection("users").document(myUid)
                    .collection("friends").document(theirUid).get()
                    .addOnSuccessListener { existing ->
                        if (existing.exists()) {
                            onResult(false, "Already friends")
                            return@addOnSuccessListener
                        }
                        val myEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
                        db.collection("users").document(theirUid)
                            .collection("friendRequests").document(myUid)
                            .set(mapOf(
                                "fromUid"     to myUid,
                                "displayName" to myName,
                                "email"       to myEmail,
                                "createdAt"   to FieldValue.serverTimestamp()
                            ))
                            .addOnSuccessListener { onResult(true, "Request sent!") }
                            .addOnFailureListener { onResult(false, it.message ?: "Failed") }
                    }
                    .addOnFailureListener { onResult(false, it.message ?: "Failed") }
            }
            .addOnFailureListener { onResult(false, "Lookup failed: ${it.message}") }
    }

    /**
     * Accept an incoming friend request from [fromUid].
     * Writes a `friends` entry on both sides and deletes the request doc.
     */
    fun acceptFriendRequest(fromUid: String, fromName: String, onDone: () -> Unit = {}) {
        val myUid   = uid ?: return
        val myName  = FirebaseAuth.getInstance().currentUser?.displayName
            ?: FirebaseAuth.getInstance().currentUser?.email ?: "User"
        val batch = db.batch()

        // My friends list
        batch.set(
            db.collection("users").document(myUid).collection("friends").document(fromUid),
            mapOf("displayName" to fromName, "since" to FieldValue.serverTimestamp())
        )
        // Their friends list
        batch.set(
            db.collection("users").document(fromUid).collection("friends").document(myUid),
            mapOf("displayName" to myName, "since" to FieldValue.serverTimestamp())
        )
        // Delete the request
        batch.delete(
            db.collection("users").document(myUid).collection("friendRequests").document(fromUid)
        )
        batch.commit().addOnCompleteListener { onDone() }
    }

    data class FriendSuggestion(val uid: String, val displayName: String, val email: String)

    /**
     * Given a list of email addresses (from the phone's contacts), find which
     * ones belong to app users. Returns at most one result per email.
     * Firestore `whereIn` is capped at 30 items, so large lists are batched.
     */
    fun findUsersByEmails(
        emails:   List<String>,
        onResult: (List<FriendSuggestion>) -> Unit
    ) {
        val myUid   = uid   ?: run { onResult(emptyList()); return }
        val myEmail = FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: ""

        val filtered = emails
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != myEmail }
            .distinct()

        if (filtered.isEmpty()) { onResult(emptyList()); return }

        // Fetch current friend UIDs so we can exclude them
        db.collection("users").document(myUid).collection("friends").get()
            .addOnSuccessListener { friendSnap ->
                val friendUids = friendSnap.documents.map { it.id }.toSet()

                val results = mutableListOf<FriendSuggestion>()
                var pending = filtered.size

                // Read each emailIndex document by ID — no collection query needed
                filtered.forEach { email ->
                    db.collection("emailIndex").document(email).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val theirUid = doc.getString("uid") ?: ""
                                if (theirUid.isNotEmpty() && theirUid != myUid && theirUid !in friendUids) {
                                    results += FriendSuggestion(
                                        uid         = theirUid,
                                        displayName = doc.getString("displayName") ?: email.substringBefore("@"),
                                        email       = email
                                    )
                                }
                            }
                            if (--pending == 0) onResult(results)
                        }
                        .addOnFailureListener { if (--pending == 0) onResult(results) }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /** Decline (delete) an incoming friend request. */
    fun declineFriendRequest(fromUid: String, onDone: () -> Unit = {}) {
        val myUid = uid ?: return
        db.collection("users").document(myUid)
            .collection("friendRequests").document(fromUid)
            .delete()
            .addOnCompleteListener { onDone() }
    }

    /** Remove an existing friend from both sides. */
    fun removeFriend(friendUid: String, onDone: () -> Unit = {}) {
        val myUid = uid ?: return
        val batch = db.batch()
        batch.delete(db.collection("users").document(myUid).collection("friends").document(friendUid))
        batch.delete(db.collection("users").document(friendUid).collection("friends").document(myUid))
        batch.commit().addOnCompleteListener { onDone() }
    }

    /** Load all pending incoming friend requests for the current user. */
    fun loadFriendRequests(onResult: (List<FriendRequest>) -> Unit) {
        val myUid = uid ?: run { onResult(emptyList()); return }
        db.collection("users").document(myUid)
            .collection("friendRequests")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    FriendRequest(
                        fromUid     = doc.getString("fromUid")     ?: return@mapNotNull null,
                        displayName = doc.getString("displayName") ?: "Unknown",
                        email       = doc.getString("email")       ?: ""
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /**
     * Load today's stats summary for all friends.
     * Each friend's profile is fetched first; if they have shareStats=false
     * the stats fields are returned as zeros with shareStats=false.
     */
    fun loadFriendsSummary(dateKey: String, onResult: (List<FriendSummary>) -> Unit) {
        val myUid = uid ?: run { onResult(emptyList()); return }
        db.collection("users").document(myUid)
            .collection("friends")
            .get()
            .addOnSuccessListener { friendSnap ->
                if (friendSnap.isEmpty) { onResult(emptyList()); return@addOnSuccessListener }

                val friendDocs = friendSnap.documents
                val results    = arrayOfNulls<FriendSummary>(friendDocs.size)
                var pending    = friendDocs.size

                fun checkDone() {
                    if (--pending == 0) onResult(results.filterNotNull())
                }

                friendDocs.forEachIndexed { idx, friendDoc ->
                    val friendUid  = friendDoc.id
                    val friendName = friendDoc.getString("displayName") ?: "Friend"

                    // Fetch their profile to get target + shareStats flag
                    db.collection("users").document(friendUid).get()
                        .addOnSuccessListener { profile ->
                            val shareStats  = profile.getBoolean("shareStats") ?: true
                            val targetCal   = profile.getLong("targetCalories")?.toInt() ?: 0

                            if (!shareStats) {
                                results[idx] = FriendSummary(friendUid, friendName, 0, targetCal, 0, false)
                                checkDone()
                                return@addOnSuccessListener
                            }

                            // Fetch today's food log + workouts in parallel
                            var subPending = 2
                            var consumed = 0
                            var burned   = 0

                            fun subDone() {
                                if (--subPending == 0) {
                                    results[idx] = FriendSummary(
                                        friendUid, friendName, consumed, targetCal, burned, true
                                    )
                                    checkDone()
                                }
                            }

                            db.collection("users").document(friendUid)
                                .collection("foodLog").document(dateKey).get()
                                .addOnSuccessListener { foodDoc ->
                                    @Suppress("UNCHECKED_CAST")
                                    val entries = foodDoc.get("entries") as? List<Map<String, Any>> ?: emptyList()
                                    consumed = entries.sumOf { (it["cal"] as? Long)?.toInt() ?: 0 }
                                    subDone()
                                }
                                .addOnFailureListener { subDone() }

                            db.collection("users").document(friendUid)
                                .collection("workouts").document(dateKey).get()
                                .addOnSuccessListener { wDoc ->
                                    @Suppress("UNCHECKED_CAST")
                                    val entries = wDoc.get("entries") as? List<Map<String, Any>> ?: emptyList()
                                    burned = entries.sumOf { (it["calories"] as? Long)?.toInt() ?: 0 }
                                    subDone()
                                }
                                .addOnFailureListener { subDone() }
                        }
                        .addOnFailureListener {
                            results[idx] = FriendSummary(friendUid, friendName, 0, 0, 0, false)
                            checkDone()
                        }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /**
     * Pull the user's profile / nutrition targets from Firestore and store
     * them in SharedPreferences.  Useful on first login from a new device.
     */
    fun pullProfile(prefs: SharedPreferences, onDone: () -> Unit = {}) {
        val uid = uid ?: run { onDone(); return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    prefs.edit().apply {
                        doc.getLong("targetCalories") ?.let { putInt("target_calories",  it.toInt()) }
                        doc.getLong("targetProteinG") ?.let { putInt("target_protein_g", it.toInt()) }
                        doc.getLong("targetFatG")     ?.let { putInt("target_fat_g",     it.toInt()) }
                        doc.getLong("targetCarbsG")   ?.let { putInt("target_carbs_g",   it.toInt()) }
                        doc.getString("name")          ?.let { putString("user_name",      it) }
                        doc.getLong("weightValue")    ?.let { putInt("weight_value",     it.toInt()) }
                        doc.getString("weightUnit")    ?.let { putString("weight_unit",    it) }
                        doc.getLong("heightCm")       ?.let { putInt("height_cm",        it.toInt()) }
                        doc.getLong("age")            ?.let { putInt("user_age",         it.toInt()) }
                        doc.getString("gender")        ?.let { putString("user_gender",    it) }
                        doc.getString("goalType")      ?.let { putString("goal_type",      it) }
                        doc.getString("activityLevel") ?.let { putString("activity_level", it) }
                        doc.getBoolean("onboardingComplete")?.let { putBoolean("onboarding_complete", it) }
                        doc.getBoolean("shareStats")        ?.let { putBoolean("share_stats",         it) }
                    }.apply()
                }
                onDone()
            }
            .addOnFailureListener { onDone() }
    }
}

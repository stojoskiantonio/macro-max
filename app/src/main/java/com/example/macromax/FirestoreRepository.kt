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
        val data: Map<String, Any> = mapOf(
            "targetCalories"  to prefs.getInt("target_calories",  0),
            "targetProteinG"  to prefs.getInt("target_protein_g", 0),
            "targetFatG"      to prefs.getInt("target_fat_g",     0),
            "targetCarbsG"    to prefs.getInt("target_carbs_g",   0),
            "name"            to (prefs.getString("user_name",      "") ?: ""),
            "weightValue"     to prefs.getInt("weight_value",     0),
            "weightUnit"      to (prefs.getString("weight_unit",    "kg") ?: "kg"),
            "heightCm"        to prefs.getInt("height_cm",        0),
            "age"             to prefs.getInt("user_age",         0),
            "gender"          to (prefs.getString("user_gender",    "") ?: ""),
            "goalType"        to (prefs.getString("goal_type",      "") ?: ""),
            "activityLevel"   to (prefs.getString("activity_level", "") ?: ""),
            "updatedAt"       to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
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
                    }.apply()
                }
                onDone()
            }
            .addOnFailureListener { onDone() }
    }
}

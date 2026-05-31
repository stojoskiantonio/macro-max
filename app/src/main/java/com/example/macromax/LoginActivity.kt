package com.example.macromax

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.ActivityResultLauncher
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient
    private val facebookCallbackManager = CallbackManager.Factory.create()
    private lateinit var facebookLauncher: ActivityResultLauncher<Collection<String>>

    // Notification permission (Android 13+) — asked once at app launch
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Proceed regardless of whether the user granted or denied
            finishSetup()
        }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signInWithCredential(GoogleAuthProvider.getCredential(account.idToken, null), method = "google")
        } catch (e: ApiException) {
            toast("Google sign-in failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Facebook: register launcher + callback before onStart
        val fbContract = LoginManager.getInstance().createLogInActivityResultContract(facebookCallbackManager)
        facebookLauncher = registerForActivityResult(fbContract) { /* results dispatched to callback */ }
        LoginManager.getInstance().registerCallback(
            facebookCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    signInWithCredential(
                        com.google.firebase.auth.FacebookAuthProvider.getCredential(
                            result.accessToken.token
                        ),
                        method = "facebook"
                    )
                }
                override fun onCancel() {}
                override fun onError(error: FacebookException) { toast("Facebook sign-in failed") }
            }
        )

        // Ask for notification permission before anything else (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishSetup()
        }
    }

    /** Runs after the notification permission prompt is handled (or immediately on < API 33). */
    private fun finishSetup() {
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { goToMain(); return }

        setupLanguageButton()
        setupGoogle()

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)

        findViewById<MaterialButton>(R.id.btnSignIn).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            if (email.isEmpty() || pass.isEmpty()) { toast("Enter email and password"); return@setOnClickListener }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    result.user?.let { u ->
                        FirestoreRepository.registerUser(u.uid, u.email ?: email, u.displayName ?: "")
                    }
                    Analytics.logLogin("email")
                    goToMain()
                }
                .addOnFailureListener { toast(it.message ?: "Sign-in failed") }
        }

        findViewById<MaterialButton>(R.id.btnSignUp).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            if (email.isEmpty() || pass.isEmpty()) { toast("Enter email and password"); return@setOnClickListener }
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    result.user?.let { u ->
                        FirestoreRepository.registerUser(u.uid, u.email ?: email, u.displayName ?: "")
                    }
                    Analytics.logSignUp("email")
                    goToMain()
                }
                .addOnFailureListener { toast(it.message ?: "Account creation failed") }
        }

        findViewById<MaterialButton>(R.id.btnGoogle).setOnClickListener {
            if (!::googleClient.isInitialized) {
                toast("Add google-services.json to enable Google Sign-In")
                return@setOnClickListener
            }
            googleClient.signOut().addOnCompleteListener {
                googleLauncher.launch(googleClient.signInIntent)
            }
        }

        findViewById<MaterialButton>(R.id.btnFacebook).setOnClickListener {
            facebookLauncher.launch(listOf("email", "public_profile"))
        }

        findViewById<MaterialButton>(R.id.btnGuest).setOnClickListener {
            showGuestNameDialog()
        }
    }

    private fun setupLanguageButton() {
        val btn = findViewById<TextView>(R.id.btnLanguage)
        val isMk = AppCompatDelegate.getApplicationLocales()[0]?.language == "mk"
        btn.text = if (isMk) "🇬🇧" else "🇲🇰"
        btn.setOnClickListener {
            val currentlyMk = AppCompatDelegate.getApplicationLocales()[0]?.language == "mk"
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(if (currentlyMk) "en" else "mk")
            )
        }
    }

    private fun setupGoogle() {
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resId == 0) return  // google-services.json not added yet
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(resId))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithCredential(credential: AuthCredential, method: String) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                result.user?.let { u ->
                    FirestoreRepository.registerUser(u.uid, u.email ?: "", u.displayName ?: "")
                }
                if (result.additionalUserInfo?.isNewUser == true) Analytics.logSignUp(method)
                else Analytics.logLogin(method)
                goToMain()
            }
            .addOnFailureListener { toast(it.message ?: "Sign-in failed") }
    }

    private fun showGuestNameDialog() {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.hint_name)
            setPadding(
                (24 * resources.displayMetrics.density).toInt(), 0,
                (24 * resources.displayMetrics.density).toInt(), 0
            )
        }
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        layout.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guest_name_title)
            .setView(layout)
            .setPositiveButton(R.string.btn_next) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { getString(R.string.btn_guest) }
                getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                    .edit().putString("guest_name", name).apply()
                auth.signInAnonymously()
                    .addOnSuccessListener { Analytics.logLogin("guest"); goToMain() }
                    .addOnFailureListener { toast(it.message ?: "Guest sign-in failed") }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun goToMain() {
        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val currentUid = auth.currentUser?.uid.orEmpty()
        val storedUid  = prefs.getString("user_uid", "").orEmpty()
        val isNewDevice = currentUid != storedUid

        if (isNewDevice) {
            prefs.edit().clear().putString("user_uid", currentUid).apply()
        }

        // Fallback registration for returning users who are already logged in
        // when the app opens (didn't go through a fresh sign-in callback above).
        auth.currentUser?.let { u ->
            FirestoreRepository.registerUser(u.uid, u.email ?: "", u.displayName ?: "")
        }

        val goNext = {
            val dest = if (prefs.getBoolean("onboarding_complete", false)) MainActivity::class.java
                       else AgeSelectionActivity::class.java
            startActivity(Intent(this, dest))
            finish()
        }

        // On a new device, pull profile + today's data from Firestore first
        if (isNewDevice) {
            val dateKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                            .format(java.util.Date())
            FirestoreRepository.pullProfile(prefs) {
                FirestoreRepository.pullTodayData(dateKey, prefs) { goNext() }
            }
        } else {
            goNext()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

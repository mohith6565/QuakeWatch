package com.example.quakewatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { goMain(); return }
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val e = etEmail.text.toString().trim()
            val p = etPassword.text.toString()
            if (e.isEmpty() || p.length < 6) { toast("Enter email and password (min 6 chars)"); return@setOnClickListener }
            auth.signInWithEmailAndPassword(e, p)
                .addOnSuccessListener { goMain() }
                .addOnFailureListener { toast(it.message ?: "Login failed") }
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val e = etEmail.text.toString().trim()
            val p = etPassword.text.toString()
            if (e.isEmpty() || p.length < 6) { toast("Enter email and password (min 6 chars)"); return@setOnClickListener }
            auth.createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener { goMain() }
                .addOnFailureListener { toast(it.message ?: "Registration failed") }
        }

        findViewById<Button>(R.id.btnGoogle).setOnClickListener { googleSignIn() }
    }

    private fun googleSignIn() {
        val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cm = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = cm.getCredential(this@LoginActivity, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val fbCred = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(fbCred)
                        .addOnSuccessListener { goMain() }
                        .addOnFailureListener { toast(it.message ?: "Google sign-in failed") }
                } else {
                    toast("Unexpected credential type")
                }
            } catch (e: GetCredentialException) {
                toast("Google sign-in failed: ${e.message}")
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
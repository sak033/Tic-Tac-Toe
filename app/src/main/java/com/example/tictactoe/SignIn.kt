package com.example.tictactoe

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class SignIn : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 1001          // any unique int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin)

        /* ---------- 1. Firebase instance ---------- */
        firebaseAuth = FirebaseAuth.getInstance()

        /* ---------- 2. Google sign-in options ---------- */
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))   // from google-services.json
            .requestEmail()
            .build()

        /* ---------- 3. Google sign-in client ---------- */
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        /* ---------- 4. Click on TextView to launch sign-in with account picker ---------- */
        findViewById<TextView>(R.id.googleSignIn).setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }
        }
    }

    /* ---------- 5. Handle result ---------- */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)  // hand off to Firebase
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /* ---------- 6. Exchange Google token for Firebase credentials ---------- */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    val uid = user?.uid
                    val name = user?.displayName

                    // ✅ Save name in Firebase Database under "userProfiles/{uid}/name"
                    if (uid != null && name != null) {
                        val db =
                            com.google.firebase.database.FirebaseDatabase.getInstance().reference
                        db.child("userProfiles").child(uid).child("name").setValue(name)
                            .addOnSuccessListener {
                                // Navigate after saving name successfully
                                startActivity(Intent(this, HomeActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to save user name", Toast.LENGTH_SHORT)
                                    .show()
                            }
                    } else {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()


                    }
                }
            }
    }

}
package com.example.tictactoe

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MatchActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference.child("gameRooms")
    private val uid = FirebaseAuth.getInstance().currentUser!!.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = "Looking for opponent…"

        // Try to find a waiting room
        db.orderByChild("playerO").equalTo("waiting")
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    // ✅ Join as Player O
                    val roomId = snap.children.first().key!!
                    db.child(roomId).child("playerO").setValue(uid)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Joined existing room as O", Toast.LENGTH_SHORT).show()
                            launchGame(roomId, "O")
                        }
                } else {
                    // ❌ No room found → create one as Player X
                    val roomId = db.push().key!!
                    val emptyBoard = List(9) { "" }

                    val newRoom = mapOf(
                        "playerX" to uid,
                        "playerO" to "waiting",
                        "board" to emptyBoard,
                        "turn" to "X",
                        "winner" to ""
                    )

                    db.child(roomId).setValue(newRoom)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Created new room as X", Toast.LENGTH_SHORT).show()
                            launchGame(roomId, "X")
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun launchGame(roomId: String, myMark: String) {
        val i = Intent(this, GameActivity::class.java)
        i.putExtra("ROOM_ID", roomId)
        i.putExtra("MY_MARK", myMark)
        startActivity(i)
        finish()
    }
}
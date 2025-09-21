package com.example.tictactoe

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*


class GameActivity : AppCompatActivity() {

    private lateinit var buttons: Array<Button>
    private lateinit var textResult: TextView
    private lateinit var textSubResult: TextView
    private lateinit var playAgainText: TextView

    private var currentPlayer = "X"
    private var board = Array(9) { "" }

    private lateinit var roomId: String
    private lateinit var myMark: String
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val uid by lazy { FirebaseAuth.getInstance().currentUser!!.uid }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        roomId  = intent.getStringExtra("ROOM_ID")!!
        myMark  = intent.getStringExtra("MY_MARK")!!
        textResult     = findViewById(R.id.textResult)
        textSubResult  = findViewById(R.id.textSubResult)
        playAgainText  = findViewById(R.id.textView10)

        buttons = arrayOf(
            findViewById(R.id.button0), findViewById(R.id.button1), findViewById(R.id.button2),
            findViewById(R.id.button3), findViewById(R.id.button4), findViewById(R.id.button5),
            findViewById(R.id.button6), findViewById(R.id.button7), findViewById(R.id.button8)
        )

        for (i in buttons.indices) {
            buttons[i].setBackgroundResource(R.drawable.button_default)
            buttons[i].setOnClickListener { makeMove(i) }
        }

        listenToRoom()
        playAgainText.setOnClickListener { resetGameInFirebase() }
    }

    /* ---------- makeMove(): one cell tap ---------- */
    private fun makeMove(index: Int) {
        if (board[index].isNotEmpty() || currentPlayer != myMark) return

        db.child("gameRooms").child(roomId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutable: MutableData): Transaction.Result {
                val room = mutable.value as? Map<String, Any?> ?: return Transaction.success(mutable)
                val boardList = (room["board"] as? ArrayList<String>)?.toMutableList() ?: return Transaction.success(mutable)
                val turn      = room["turn"]  as? String ?: return Transaction.success(mutable)
                val winner    = room["winner"] as? String ?: ""

                if (winner.isNotEmpty() || turn != myMark || boardList[index].isNotEmpty())
                    return Transaction.success(mutable)  // invalid

                boardList[index] = myMark
                val nextTurn = if (myMark == "X") "O" else "X"

                val newRoom = HashMap(room)
                newRoom["board"] = boardList
                newRoom["turn"]  = nextTurn

                val combos = arrayOf(
                    intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8),
                    intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8),
                    intArrayOf(0,4,8), intArrayOf(2,4,6)
                )
                when {
                    combos.any { (a,b,c)-> boardList[a]==myMark&&boardList[b]==myMark&&boardList[c]==myMark } ->
                        newRoom["winner"] = myMark
                    boardList.all { it.isNotEmpty() }   -> newRoom["winner"] = "DRAW"
                }

                mutable.value = newRoom
                return Transaction.success(mutable)
            }

            override fun onComplete(
                e: DatabaseError?,
                committed: Boolean,
                snap: DataSnapshot?
            ) {
                if (e != null) {
                    // a rules or permission failure → opponent never sees the move
                    android.util.Log.e("GameActivity", "Transaction error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@GameActivity,
                            "Move rejected: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (!committed) {
                    // Transaction aborted for another reason (race condition etc.)
                    android.util.Log.w("GameActivity", "Transaction not committed")
                } else {
                    // Transaction success → update stats if game ended
                    val winner = snap?.child("winner")?.getValue(String::class.java) ?: ""
                    if (winner.isNotEmpty()) updateStatsInFirebase(winner, snap!!)
                }
            }

        })
    }

    /* ---------- listener: keep board & UI in sync ---------- */
    private fun listenToRoom() {
        db.child("gameRooms").child(roomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val boardSnap = s.child("board")
                    val boardList = if (boardSnap.exists() && boardSnap.childrenCount == 9L) {
                        boardSnap.children.map { it.getValue(String::class.java) ?: "" }
                    } else List(9) { "" }

                    currentPlayer = s.child("turn").getValue(String::class.java) ?: "X"
                    val winner = s.child("winner").getValue(String::class.java) ?: ""

                    board = boardList.toTypedArray()
                    buttons.forEachIndexed { i, btn ->
                        btn.text = board[i]
                        btn.isEnabled = board[i].isEmpty() && currentPlayer == myMark && winner.isEmpty()
                        when (board[i]) {
                            "X" -> { btn.setBackgroundResource(R.drawable.button_x); btn.setTextColor(Color.parseColor("#2196F3")) }
                            "O" -> { btn.setBackgroundResource(R.drawable.button_o); btn.setTextColor(Color.parseColor("#F44336")) }
                            else -> { btn.setBackgroundResource(R.drawable.button_default); btn.text = "" }
                        }
                    }

                    if (winner.isNotEmpty()) {
                        val youWon = winner == myMark
                        when (winner) {
                            "DRAW" -> showResult("Draw!", "#000000", "It's a draw")
                            else -> {
                                if (youWon) {
                                    showResult("You Win!", "#4CAF50", "Congratulations")
                                } else {
                                    showResult("You Lost!", "#F44336", "Good luck next time")
                                }
                            }
                        }
                        disableAllButtons()
                    }

                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    /* ---------- update win/loss/draw counters ---------- */
    private fun updateStatsInFirebase(winner: String, roomSnap: DataSnapshot) {
        Log.d("GameActivity", "updateStatsInFirebase called with winner: $winner")

        val playerX = roomSnap.child("playerX").getValue(String::class.java) ?: return
        val playerO = roomSnap.child("playerO").getValue(String::class.java) ?: return
        val statsRef = db.child("userStats")

        Log.d("GameActivity", "playerX = $playerX, playerO = $playerO")

        // Update stats based on winner
        when (winner) {
            "DRAW" -> {
                statsRef.child(playerX).child("draws").runTransaction(increment())
                statsRef.child(playerO).child("draws").runTransaction(increment())
            }
            "X" -> {
                statsRef.child(playerX).child("wins").runTransaction(increment())
                statsRef.child(playerO).child("losses").runTransaction(increment())
            }
            "O" -> {
                statsRef.child(playerO).child("wins").runTransaction(increment())
                statsRef.child(playerX).child("losses").runTransaction(increment())
            }
        }

        val currentUid = FirebaseAuth.getInstance().currentUser!!.uid
        val myResult = when {
            winner == "DRAW" -> "DRAW"
            (myMark == winner) -> "WON"
            else -> "LOST"
        }
        val opponentResult = when (myResult) {
            "WON" -> "LOST"
            "LOST" -> "WON"
            else -> "DRAW"
        }

        val opponentId = if (currentUid == playerX) playerO else playerX
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        // Save result for current user
        val userProfiles = FirebaseDatabase.getInstance().getReference("userProfiles")
        userProfiles.child(opponentId).child("name").get()
            .addOnSuccessListener { snapshot1 ->
                val opponentName = snapshot1.getValue(String::class.java) ?: opponentId
                // For the current user: show opponent + opponent's result
                saveGameResult(opponentName, opponentResult) // ✅

                userProfiles.child(currentUid).child("name").get()
                    .addOnSuccessListener { snapshot2 ->
                        val myName = snapshot2.getValue(String::class.java) ?: currentUid
                        // For the opponent: show your name + your result
                        saveOpponentGameResultForThem(opponentId, myName, date, myResult) // ✅
                    }

                    .addOnFailureListener {
                        Log.e("HistorySave", "Failed to fetch current user's name", it)
                    }
            }
            .addOnFailureListener {
                Log.e("HistorySave", "Failed to fetch opponent name", it)
            }
    }




    private fun increment(): Transaction.Handler = object : Transaction.Handler {
        override fun doTransaction(m: MutableData): Transaction.Result {
            val cur = m.getValue(Int::class.java) ?: 0
            m.value = cur + 1
            return Transaction.success(m)
        }
        override fun onComplete(e: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {}
    }

    /* ---------- helpers ---------- */
    private fun disableAllButtons() = buttons.forEach { it.isEnabled = false }

    private fun showResult(result: String, colorHex: String, subText: String) {
        textResult.text = result
        textResult.setTextColor(Color.parseColor(colorHex))
        textResult.visibility = View.VISIBLE
        textSubResult.text = subText
        textSubResult.visibility = View.VISIBLE
    }

    private fun resetGameInFirebase() {
        val reset = mapOf("board" to List(9) { "" }, "turn" to "X", "winner" to "")
        db.child("gameRooms").child(roomId).updateChildren(reset)
    }

    private fun saveGameResult(opponent: String, result: String) {
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val uid = FirebaseAuth.getInstance().currentUser!!.uid

        val gameItem = mapOf(
            "opponent" to opponent,
            "date" to date,
            "result" to result
        )

        FirebaseDatabase.getInstance()
            .getReference("userHistory")
            .child(uid)
            .push()
            .setValue(gameItem)
    }


    private fun saveOpponentGameResultForThem(opponentId: String, myName: String, date: String, result: String) {
        val gameItem = mapOf(
            "opponent" to myName,
            "date" to date,
            "result" to result
        )

        FirebaseDatabase.getInstance()
            .getReference("userHistory")
            .child(opponentId)
            .push()
            .setValue(gameItem)
    }


}



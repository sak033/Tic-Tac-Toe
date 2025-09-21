package com.example.tictactoe

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        /* ---------- 1. Show player name ---------- */
        val user  = FirebaseAuth.getInstance().currentUser
        val name  = user?.displayName ?: user?.email?.substringBefore('@') ?: "Player"
        findViewById<TextView>(R.id.textView4).text = name

        /* ---------- 2. Load & display stats ---------- */
        val uid = user?.uid ?: return
        val statsRef = FirebaseDatabase.getInstance().reference
            .child("userStats").child(uid)

        statsRef.get().addOnSuccessListener { snap ->
            val wins   = snap.child("wins").getValue(Int::class.java) ?: 0
            val loss   = snap.child("losses").getValue(Int::class.java) ?: 0
            val draws  = snap.child("draws").getValue(Int::class.java) ?: 0

            findViewById<TextView>(R.id.textView5).text = wins.toString()
            findViewById<TextView>(R.id.textView7).text = loss.toString()
            findViewById<TextView>(R.id.textView9).text = draws.toString()
        }

        /* ---------- 3. Bottom-nav setup ---------- */
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        nav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED

        nav.setOnItemSelectedListener {
            if (it.itemId == R.id.nav_game) {
                startActivity(Intent(this, MatchActivity::class.java))
                true
            } else false
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val historyRef = FirebaseDatabase.getInstance().reference
            .child("userHistory").child(uid)

        historyRef.get().addOnSuccessListener { snapshot ->
            val historyList = mutableListOf<GameHistoryItem>()
            for (gameSnap in snapshot.children) {
                val opponent = gameSnap.child("opponent").getValue(String::class.java) ?: "Unknown"
                val date = gameSnap.child("date").getValue(String::class.java) ?: "?"
                val result = gameSnap.child("result").getValue(String::class.java) ?: "?"

                historyList.add(GameHistoryItem(opponent, date, result))
            }

            recyclerView.adapter = GameHistoryAdapter(historyList)
        }


    }
}

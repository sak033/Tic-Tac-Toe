package com.example.tictactoe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameHistoryAdapter(private val historyList: List<GameHistoryItem>) :
    RecyclerView.Adapter<GameHistoryAdapter.GameHistoryViewHolder>() {

    class GameHistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvOpponent: TextView = view.findViewById(R.id.tvOpponent)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvResult: TextView = view.findViewById(R.id.tvResult)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.game_history_item, parent, false)
        return GameHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameHistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvOpponent.text = item.opponent
        holder.tvDate.text = item.date
        holder.tvResult.text = item.result

        when (item.result.uppercase()) {
            "WON" -> holder.tvResult.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
            "LOST" -> holder.tvResult.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            "DRAW" -> holder.tvResult.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }
    }

    override fun getItemCount(): Int = historyList.size
}

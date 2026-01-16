package com.parametican.alertarift

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Color

class LogsActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogsAdapter
    private lateinit var btnClear: Button
    private lateinit var btnBack: Button
    private lateinit var spinnerFilter: Spinner
    private lateinit var tvLogCount: TextView
    private lateinit var switchAutoScroll: Switch
    
    private var autoScroll = true
    private var currentFilter: AppLogger.LogLevel? = null
    
    private val logListener: (AppLogger.LogEntry) -> Unit = { entry ->
        runOnUiThread {
            if (currentFilter == null || entry.level == currentFilter) {
                adapter.addLog(entry)
                updateLogCount()
                if (autoScroll) {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Layout programático
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(16, 16, 16, 16)
        }
        
        // Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        btnBack = Button(this).apply {
            text = "← Volver"
            setBackgroundColor(Color.parseColor("#374151"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { finish() }
        }
        headerLayout.addView(btnBack)
        
        val titleView = TextView(this).apply {
            text = "📋 LOGS DEL SISTEMA"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16
            }
        }
        headerLayout.addView(titleView)
        
        mainLayout.addView(headerLayout)
        
        // Filtros
        val filterLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        
        val filterLabel = TextView(this).apply {
            text = "Filtrar: "
            setTextColor(Color.WHITE)
        }
        filterLayout.addView(filterLabel)
        
        spinnerFilter = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val filterOptions = arrayOf("Todos", "INFO", "SUCCESS", "WARNING", "ERROR", "NETWORK", "ALERT", "COMMAND", "RESPONSE")
        spinnerFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions)
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = if (position == 0) null else AppLogger.LogLevel.values()[position - 1]
                refreshLogs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        filterLayout.addView(spinnerFilter)
        
        switchAutoScroll = Switch(this).apply {
            text = "Auto-scroll"
            setTextColor(Color.WHITE)
            isChecked = true
            setOnCheckedChangeListener { _, isChecked -> autoScroll = isChecked }
        }
        filterLayout.addView(switchAutoScroll)
        
        mainLayout.addView(filterLayout)
        
        // Log count
        tvLogCount = TextView(this).apply {
            text = "0 logs"
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        mainLayout.addView(tvLogCount)
        
        // RecyclerView para logs
        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 8
            }
            setBackgroundColor(Color.parseColor("#0f0f1a"))
        }
        adapter = LogsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        mainLayout.addView(recyclerView)
        
        // Botón limpiar
        btnClear = Button(this).apply {
            text = "🗑️ LIMPIAR LOGS"
            setBackgroundColor(Color.parseColor("#dc2626"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setOnClickListener {
                AppLogger.clear()
                adapter.clear()
                updateLogCount()
            }
        }
        mainLayout.addView(btnClear)
        
        setContentView(mainLayout)
        
        // Cargar logs existentes
        refreshLogs()
        
        // Registrar listener para nuevos logs
        AppLogger.addListener(logListener)
        
        // Log de apertura
        AppLogger.info("LOGS", "📋 Pantalla de logs abierta", null)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.removeListener(logListener)
    }
    
    private fun refreshLogs() {
        val logs = if (currentFilter == null) {
            AppLogger.getLogs()
        } else {
            AppLogger.getLogsFiltered(currentFilter)
        }
        adapter.setLogs(logs)
        updateLogCount()
        if (autoScroll && adapter.itemCount > 0) {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }
    
    private fun updateLogCount() {
        tvLogCount.text = "${adapter.itemCount} logs"
    }
    
    // ============================================
    // ADAPTER
    // ============================================
    
    inner class LogsAdapter : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {
        
        private val logs = mutableListOf<AppLogger.LogEntry>()
        
        fun setLogs(newLogs: List<AppLogger.LogEntry>) {
            logs.clear()
            logs.addAll(newLogs)
            notifyDataSetChanged()
        }
        
        fun addLog(entry: AppLogger.LogEntry) {
            logs.add(entry)
            notifyItemInserted(logs.size - 1)
        }
        
        fun clear() {
            logs.clear()
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(12, 8, 12, 8)
            }
            return LogViewHolder(layout)
        }
        
        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logs[position])
        }
        
        override fun getItemCount() = logs.size
        
        inner class LogViewHolder(private val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
            
            private val tvHeader = TextView(layout.context).apply {
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            
            private val tvMessage = TextView(layout.context).apply {
                textSize = 14f
            }
            
            private val tvDetails = TextView(layout.context).apply {
                textSize = 11f
                setTextColor(Color.parseColor("#9ca3af"))
                visibility = View.GONE
            }
            
            init {
                layout.addView(tvHeader)
                layout.addView(tvMessage)
                layout.addView(tvDetails)
            }
            
            fun bind(entry: AppLogger.LogEntry) {
                tvHeader.text = "${entry.getFormattedTime()} [${entry.tag}]"
                tvMessage.text = "${entry.getIcon()} ${entry.message}"
                
                val color = when(entry.level) {
                    AppLogger.LogLevel.SUCCESS -> Color.parseColor("#22c55e")
                    AppLogger.LogLevel.WARNING -> Color.parseColor("#f59e0b")
                    AppLogger.LogLevel.ERROR -> Color.parseColor("#ef4444")
                    AppLogger.LogLevel.ALERT -> Color.parseColor("#ef4444")
                    AppLogger.LogLevel.NETWORK -> Color.parseColor("#3b82f6")
                    AppLogger.LogLevel.COMMAND -> Color.parseColor("#8b5cf6")
                    AppLogger.LogLevel.RESPONSE -> Color.parseColor("#06b6d4")
                    else -> Color.WHITE
                }
                tvMessage.setTextColor(color)
                
                if (entry.details != null) {
                    tvDetails.text = entry.details
                    tvDetails.visibility = View.VISIBLE
                } else {
                    tvDetails.visibility = View.GONE
                }
                
                // Separador visual
                layout.setBackgroundColor(
                    if (adapterPosition % 2 == 0) Color.parseColor("#0f0f1a")
                    else Color.parseColor("#1a1a2e")
                )
            }
        }
    }
}

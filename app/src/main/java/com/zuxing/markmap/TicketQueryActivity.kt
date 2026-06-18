package com.zuxing.markmap

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.zuxing.markmap.databinding.ActivityTicketQueryBinding
import com.zuxing.markmap.databinding.ItemStopStationBinding
import com.zuxing.markmap.databinding.ItemTicketBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.Locale

class TicketQueryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTicketQueryBinding
    private lateinit var ticketService: TicketService
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var firstTrainCode = ""
    private var dateShort = ""
    private var fromStationSelecting = false

    private val stationResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val code = result.data?.getStringExtra("stationCode") ?: ""
            val name = result.data?.getStringExtra("stationName") ?: ""
            if (fromStationSelecting) {
                binding.etFromStation.tag = code
                binding.etFromStation.setText(if (name.isNotEmpty()) "$name($code)" else code)
            } else {
                binding.etToStation.tag = code
                binding.etToStation.setText(if (name.isNotEmpty()) "$name($code)" else code)
            }
        }
    }

    companion object {
        val SEAT_MAP = mapOf(
            'M' to "动卧", 'O' to "二等座", 'P' to "一等座",
            'W' to "商务座", '9' to "商务座",
            'A' to "硬座", '4' to "硬卧", 'I' to "软卧"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketQueryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val calendar = Calendar.getInstance()
        selectedYear = calendar.get(Calendar.YEAR)
        selectedMonth = calendar.get(Calendar.MONTH)
        selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
        updateDateDisplay()

        setupToolbar()
        setupRecyclerView()
        initService()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvTickets.layoutManager = LinearLayoutManager(this)
        binding.rvTickets.adapter = TicketAdapter { ticket ->
            showStopStations(ticket)
        }
    }

    private fun initService() {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
//                    .header("Referer", "https://servicewechat.com/wxa51f55ab3b2655b9/144/page-frame.html")
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                Logger.e("余票请求: ${request.url}")
                val response = chain.proceed(request)
                Logger.e("余票响应码: ${response.code}")
                response
            }
            .build()
        val gson = GsonBuilder().setLenient().create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://kyfw.12306.cn/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        ticketService = retrofit.create(TicketService::class.java)
    }

    private fun setupListeners() {
        binding.tvDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                updateDateDisplay()
            }, selectedYear, selectedMonth, selectedDay).show()
        }

        binding.btnQuery.setOnClickListener { queryTickets() }

        binding.etFromStation.setOnClickListener {
            fromStationSelecting = true
            stationResultLauncher.launch(Intent(this, StationSelectActivity::class.java))
        }

        binding.etToStation.setOnClickListener {
            fromStationSelecting = false
            stationResultLauncher.launch(Intent(this, StationSelectActivity::class.java))
        }

        binding.btnTrainMap.setOnClickListener {
            val trainCode = firstTrainCode
            val date = dateShort
            if (trainCode.isEmpty()) {
                Toast.makeText(this, "请先查询余票", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, TrainMapActivity::class.java).apply {
                putExtra("trainCode", trainCode)
                putExtra("date", date)
            })
        }
    }

    private fun updateDateDisplay() {
        binding.tvDate.text = String.format(Locale.CHINA, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
    }

    private fun queryTickets() {
        val fromStation = (binding.etFromStation.tag as? String)?.trim() ?: ""
        val toStation = (binding.etToStation.tag as? String)?.trim() ?: ""

        if (fromStation.isEmpty()) {
            Toast.makeText(this, "请选择出发站", Toast.LENGTH_SHORT).show()
            return
        }
        if (toStation.isEmpty()) {
            Toast.makeText(this, "请选择到达站", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnTrainMap.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    val date = String.format(Locale.CHINA, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                    dateShort = date.replace("-", "")
                    ticketService.queryTickets(date, fromStation, toStation).execute().body()
                } ?: TicketQueryResponse(status = false)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (body.status == true) {
                        val data = body.data
                        val stationMap = data?.map ?: emptyMap()
                        val result = data?.result ?: emptyList()

                        if (result.isEmpty()) {
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.rvTickets.visibility = View.GONE
                        } else {
                            val tickets = parseResults(result, stationMap)
                            firstTrainCode = extractTrainCode(tickets.firstOrNull()?.stationTrainCode ?: "")
                            binding.tvEmpty.visibility = View.GONE
                            binding.rvTickets.visibility = View.VISIBLE
                            binding.btnTrainMap.visibility = View.VISIBLE
                            (binding.rvTickets.adapter as? TicketAdapter)?.submitList(tickets)
                        }
                    } else {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvTickets.visibility = View.GONE
                        Logger.e("余票查询失败")
                        Toast.makeText(this@TicketQueryActivity, "查询失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: JsonSyntaxException) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvTickets.visibility = View.GONE
                    Logger.e("余票请求失败(JSON解析): ${e.localizedMessage}")
                    Toast.makeText(this@TicketQueryActivity, "查询失败，服务器返回格式异常", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvTickets.visibility = View.GONE
                    Logger.e("余票请求失败: ${e.localizedMessage}", e)
                    Toast.makeText(this@TicketQueryActivity, "请求失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractTrainCode(stationTrainCode: String): String {
        val match = Regex("^([A-Z]+\\d+)").find(stationTrainCode)
        return match?.groupValues?.getOrNull(1) ?: stationTrainCode
    }

    private fun parseResults(result: List<String>, stationMap: Map<String, String>): List<TicketInfo> {
        val tickets = mutableListOf<TicketInfo>()

        for (item in result) {
            val parts = item.split("|")
            if (parts.size < 40) continue

            val trainNo = parts[2]
            val stationTrainCode = parts[3]
            val fromTelecode = parts[6]
            val toTelecode = parts[7]
            val fromStation = stationMap[fromTelecode] ?: fromTelecode
            val toStation = stationMap[toTelecode] ?: toTelecode
            val startTime = formatTimeStr(parts[8])
            val arriveTime = formatTimeStr(parts[9])
            val duration = formatTimeStr(parts[10])
            val canBuy = parts[11] == "Y"
            val date = parts.getOrElse(13) { "" }.let {
                if (it.length >= 8) "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" else it
            }

            val seats = parseSeats(parts[34])
            val prices = parsePrices(parts[39])

            tickets.add(TicketInfo(trainNo, stationTrainCode, fromStation, toStation, fromTelecode, toTelecode, startTime, arriveTime, duration, canBuy, seats, prices, date))
        }

        return tickets
    }

    private fun parseSeats(seatStr: String): List<String> {
        val seen = mutableSetOf<String>()
        for (c in seatStr) {
            if (c != '0') {
                SEAT_MAP[c]?.let { seen.add(it) }
            }
        }
        return seen.toList()
    }

    private fun parsePrices(priceStr: String): Map<String, Int> {
        val prices = mutableMapOf<String, Int>()
        val regex = Regex("([A-Z])(\\d{3,4})")
        regex.findAll(priceStr).forEach { match ->
            val code = match.groupValues[1][0]
            val price = match.groupValues[2].toIntOrNull() ?: 0
            SEAT_MAP[code]?.let { seatName -> prices[seatName] = price }
        }
        return prices
    }

    private fun formatTimeStr(time: String): String {
        if (time.isBlank() || time.length < 4) return time
        return "${time.substring(0, 2)}:${time.substring(2, 4)}"
    }

    private fun showStopStations(ticket: TicketInfo) {
        val stopService = Retrofit.Builder()
            .baseUrl("https://mobile.12306.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrainService::class.java)

        val dialogView = layoutInflater.inflate(R.layout.dialog_stop_stations, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rvStopStations)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "${ticket.stationTrainCode} 停靠站"

        rv.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .create()

        dialog.show()

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val trainDate = ticket.date.replace("-", "")
                val response = withContext(Dispatchers.IO) {
                    stopService.getStopStation(ticket.trainNo, trainDate, ticket.fromTelecode).execute()
                }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body()?.status == true) {
                        val stops = response.body()?.data?.trainStopInfo ?: emptyList()
                        rv.adapter = StopStationAdapter(stops)
                    } else {
                        Toast.makeText(this@TicketQueryActivity, "获取停靠站失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Logger.e("获取停靠站失败: ${e.localizedMessage}", e)
                    Toast.makeText(this@TicketQueryActivity, "请求失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private class StopStationAdapter(
        private val stations: List<StopStationInfo>
    ) : RecyclerView.Adapter<StopStationAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemStopStationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(stations[position])
        }

        override fun getItemCount() = stations.size

        class ViewHolder(private val binding: ItemStopStationBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(s: StopStationInfo) {
                binding.tvStopNo.text = s.stationNo ?: ""
                binding.tvStopName.text = s.stationName ?: ""
                binding.tvStopArrive.text = formatStopTime(s.arriveTime)
                binding.tvStopDepart.text = formatStopTime(s.startTime)
                binding.tvStopOver.text = if (s.stopoverTime == "----") "" else s.stopoverTime ?: ""
            }

            private fun formatStopTime(time: String?): String {
                if (time.isNullOrBlank() || time == "----") return ""
                return if (time.length >= 4) "${time.substring(0, 2)}:${time.substring(2, 4)}" else time
            }
        }
    }

    private class TicketAdapter(
        private val onItemClick: (TicketInfo) -> Unit
    ) : ListAdapter<TicketInfo, TicketAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(
            private val binding: ItemTicketBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: TicketInfo) {
                binding.root.setOnClickListener { onItemClick(item) }
                binding.tvTrainCode.text = item.stationTrainCode
                binding.tvDuration.text = item.duration
                binding.tvFromStation.text = item.fromStation
                binding.tvToStation.text = item.toStation
                binding.tvStartTime.text = item.startTime
                binding.tvArriveTime.text = item.arriveTime

                if (item.canBuy) {
                    binding.tvCanBuy.text = "可订"
                    binding.tvCanBuy.setBackgroundColor(Color.parseColor("#4CAF50"))
                } else {
                    binding.tvCanBuy.text = "停售"
                    binding.tvCanBuy.setBackgroundColor(Color.parseColor("#F44336"))
                }

                binding.llSeats.removeAllViews()
                for (seat in item.seats) {
                    val price = item.prices[seat]
                    val text = if (price != null && price > 0) "$seat ¥${price / 10f}" else seat
                    val tv = TextView(binding.root.context).apply {
                        this.text = text
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#1976D2"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        val pad = (4 * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad / 2, pad, pad / 2)
                        (layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = pad
                    }
                    binding.llSeats.addView(tv)
                }
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<TicketInfo>() {
            override fun areItemsTheSame(a: TicketInfo, b: TicketInfo): Boolean = a.trainNo == b.trainNo
            override fun areContentsTheSame(a: TicketInfo, b: TicketInfo): Boolean = a == b
        }
    }
}
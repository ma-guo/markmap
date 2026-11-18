package com.zuxing.markmap

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var httpClient: OkHttpClient
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var candidateSegments: List<TicketCandidate> = emptyList()
    private var segmentFromTelecode = ""
    private var segmentToTelecode = ""
    private var segmentFromName = ""
    private var segmentToName = ""
    private var dateShort = ""
    private var fromStationSelecting = false

    private data class TicketCandidate(
        val code: String,
        val fromTelecode: String,
        val toTelecode: String,
        val fromName: String,
        val toName: String,
    )

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
        private const val MAX_CANDIDATE_TRAINS = 30
        private val HIGH_SPEED_PREFIXES = setOf('G', 'D', 'C')

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
        // 复用统一的 12306 客户端（CookieJar + UA/Referer），这里只额外加日志
        val client = createTicketHttpClient()
            .newBuilder()
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                Logger.e("余票请求: ${request.url}")
                val response = chain.proceed(request)
                Logger.e("余票响应码: ${response.code} ${response.header("Content-Type")}")
                response
            }
            .build()
        httpClient = client
        val retrofit = Retrofit.Builder()
            .baseUrl("https://kyfw.12306.cn/")
            .client(client)
            .build()
        ticketService = retrofit.create(TicketService::class.java)
    }

    private fun setupListeners() {
        binding.tvDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                val changed = year != selectedYear || month != selectedMonth || day != selectedDay
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                updateDateDisplay()
                if (changed) invalidateQueryResults()
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

        binding.btnSwapStations.setOnClickListener {
            val fromCode = binding.etFromStation.tag as? String ?: ""
            val toCode = binding.etToStation.tag as? String ?: ""
            val fromText = binding.etFromStation.text?.toString().orEmpty()
            val toText = binding.etToStation.text?.toString().orEmpty()

            binding.etFromStation.tag = toCode
            binding.etFromStation.setText(toText)
            binding.etToStation.tag = fromCode
            binding.etToStation.setText(fromText)

            invalidateQueryResults()
            binding.btnSwapStations.animate()
                .rotationBy(180f)
                .setDuration(220)
                .start()
        }

        binding.btnTrainMap.setOnClickListener {
            val segments = candidateSegments
            val date = dateShort
            if (segments.isEmpty()) {
                Toast.makeText(this, "请先查询余票", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, TrainMapActivity::class.java).apply {
                // 传入本次查询的全部候选车次及各自实际售卖的区间
                putExtra("trainCode", segments.first().code)
                putExtra("trainCodes", segments.map { it.code }.toTypedArray())
                putExtra("candidateFromTelecodes", segments.map { it.fromTelecode }.toTypedArray())
                putExtra("candidateToTelecodes", segments.map { it.toTelecode }.toTypedArray())
                putExtra("candidateFromNames", segments.map { it.fromName }.toTypedArray())
                putExtra("candidateToNames", segments.map { it.toName }.toTypedArray())
                putExtra("date", date)
                putExtra("fromTelecode", segmentFromTelecode)
                putExtra("toTelecode", segmentToTelecode)
                putExtra("fromName", segmentFromName)
                putExtra("toName", segmentToName)
            })
        }
    }

    private fun invalidateQueryResults() {
        candidateSegments = emptyList()
        binding.btnTrainMap.visibility = View.GONE
        (binding.rvTickets.adapter as? TicketAdapter)?.submitList(emptyList())
        binding.tvEmpty.visibility = View.GONE
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
        candidateSegments = emptyList()

        // 余票查询日期窗口：今天及更早改到明天，超过 10 天改到第 10 天
        val rawDate = String.format(
            Locale.CHINA, "%04d-%02d-%02d",
            selectedYear, selectedMonth + 1, selectedDay
        )
        val date = normalizeTicketQueryDate(rawDate)
        if (date != rawDate) {
            selectedYear = date.substring(0, 4).toInt()
            selectedMonth = date.substring(5, 7).toInt() - 1
            selectedDay = date.substring(8, 10).toInt()
            updateDateDisplay()
        }
        dateShort = date.replace("-", "")

        lifecycleScope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    TicketSession.warmUp(httpClient)
                    ticketService.queryTicketsAuto(date, fromStation, toStation)
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
                            candidateSegments = tickets
                                .mapNotNull { ticket ->
                                    val code = extractTrainCode(ticket.stationTrainCode)
                                    if (code.isBlank()) {
                                        null
                                    } else {
                                        TicketCandidate(
                                            code = code,
                                            fromTelecode = ticket.fromTelecode,
                                            toTelecode = ticket.toTelecode,
                                            fromName = ticket.fromStation,
                                            toName = ticket.toStation,
                                        )
                                    }
                                }
                                .sortedWith(
                                    // 1. 优先本站（用户选择的站点）本身，2. 再考虑同城站，3. G/D/C 车次更可能有走向数据
                                    compareByDescending<TicketCandidate> {
                                        it.fromTelecode.equals(fromStation, ignoreCase = true) &&
                                                it.toTelecode.equals(toStation, ignoreCase = true)
                                    }.thenByDescending {
                                        it.code.firstOrNull()?.uppercaseChar() in HIGH_SPEED_PREFIXES
                                    }
                                )
                                // 同一车次保留排序后的第一条（本站优先的那条）
                                .distinctBy { it.code }
                                .take(MAX_CANDIDATE_TRAINS)
                            segmentFromTelecode = fromStation
                            segmentToTelecode = toStation
                            segmentFromName = stationMap[fromStation]
                                ?: binding.etFromStation.text.toString().substringBefore("(").trim()
                            segmentToName = stationMap[toStation]
                                ?: binding.etToStation.text.toString().substringBefore("(").trim()
                            binding.tvEmpty.visibility = View.GONE
                            binding.rvTickets.visibility = View.VISIBLE
                            binding.btnTrainMap.visibility =
                                if (candidateSegments.isNotEmpty()) View.VISIBLE else View.GONE
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

                val context = binding.root.context
                val density = context.resources.displayMetrics.density
                val chipColor = ContextCompat.getColor(
                    context,
                    if (item.canBuy) R.color.success else R.color.error
                )
                binding.tvCanBuy.text = if (item.canBuy) "可订" else "停售"
                binding.tvCanBuy.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.tvCanBuy.background = roundedChip(chipColor, 9f * density)

                binding.llSeats.removeAllViews()
                val seatBg = ContextCompat.getColor(context, R.color.primary_container)
                val seatText = ContextCompat.getColor(context, R.color.primary)
                for (seat in item.seats) {
                    val price = item.prices[seat]
                    val text = if (price != null && price > 0) "$seat ¥${price / 10f}" else seat
                    val tv = TextView(context).apply {
                        this.text = text
                        setTextColor(seatText)
                        background = roundedChip(seatBg, 8f * density)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        val horizontal = (10 * density).toInt()
                        val vertical = (5 * density).toInt()
                        setPadding(horizontal, vertical, horizontal, vertical)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (6 * density).toInt()
                            topMargin = (4 * density).toInt()
                            bottomMargin = (4 * density).toInt()
                        }
                    }
                    binding.llSeats.addView(tv)
                }
            }

            private fun roundedChip(color: Int, radiusPx: Float): GradientDrawable {
                return GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radiusPx
                    setColor(color)
                }
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<TicketInfo>() {
            override fun areItemsTheSame(a: TicketInfo, b: TicketInfo): Boolean = a.trainNo == b.trainNo
            override fun areContentsTheSame(a: TicketInfo, b: TicketInfo): Boolean = a == b
        }
    }
}
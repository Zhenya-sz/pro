package com.fitnesslemon.app.ui.admin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.AttendanceReport
import com.fitnesslemon.app.data.models.FinancialReport
import com.fitnesslemon.app.databinding.FragmentAdminReportsBinding
import com.fitnesslemon.app.utils.PreferencesManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminReportsFragment : Fragment() {

    private var _binding: FragmentAdminReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var currentPeriod = "week"
    private var currentDate = Date()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupSpinners()
        setupListeners()
        loadAttendanceReport()
        loadFinancialReport()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Отчёты"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupSpinners() {
        val periods = arrayOf("Неделя", "Месяц", "Год")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = adapter
        binding.spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentPeriod = when (position) {
                    0 -> "week"
                    1 -> "month"
                    else -> "year"
                }
                loadAttendanceReport()
                loadFinancialReport()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnPrevDate.setOnClickListener {
            shiftDate(-1)
        }
        binding.btnNextDate.setOnClickListener {
            shiftDate(1)
        }
        binding.btnExportCsv.setOnClickListener {
            exportReport("csv")
        }
        binding.btnExportExcel.setOnClickListener {
            exportReport("xlsx")
        }
        binding.swipeRefresh.setOnRefreshListener {
            loadAttendanceReport()
            loadFinancialReport()
        }
    }

    private fun shiftDate(offset: Int) {
        val cal = Calendar.getInstance()
        cal.time = currentDate
        when (currentPeriod) {
            "week" -> cal.add(Calendar.WEEK_OF_YEAR, offset)
            "month" -> cal.add(Calendar.MONTH, offset)
            "year" -> cal.add(Calendar.YEAR, offset)
        }
        currentDate = cal.time
        updateDateDisplay()
        loadAttendanceReport()
        loadFinancialReport()
    }

    private fun updateDateDisplay() {
        val displayFormat = when (currentPeriod) {
            "week" -> {
                val cal = Calendar.getInstance()
                cal.time = currentDate
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val start = dateFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_WEEK, 6)
                val end = dateFormat.format(cal.time)
                "$start – $end"
            }
            "month" -> {
                val cal = Calendar.getInstance()
                cal.time = currentDate
                SimpleDateFormat("MMMM yyyy", Locale("ru")).format(cal.time)
            }
            else -> {
                val cal = Calendar.getInstance()
                cal.time = currentDate
                SimpleDateFormat("yyyy", Locale("ru")).format(cal.time)
            }
        }
        binding.tvDateRange.text = displayFormat
    }

    private fun loadAttendanceReport() {
        lifecycleScope.launch {
            try {
                binding.progressAttendance.visibility = View.VISIBLE
                val token = PreferencesManager.getToken() ?: return@launch
                val dateParam = dateFormat.format(currentDate)
                val response = ApiClient.adminApiService.getAttendanceReport(
                    "Bearer $token",
                    currentPeriod,
                    dateParam
                )
                if (response.isSuccessful) {
                    val report = response.body()
                    if (report != null) {
                        displayAttendanceChart(report)
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки отчёта посещаемости", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressAttendance.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun displayAttendanceChart(report: AttendanceReport) {
        val chart = binding.chartAttendance as BarChart
        val entries = mutableListOf<BarEntry>()
        report.labels.forEachIndexed { index, _ ->
            entries.add(BarEntry(index.toFloat(), report.datasets[0].data[index].toFloat()))
        }
        val dataSet = BarDataSet(entries, report.datasets[0].label).apply {
            color = Color.parseColor("#4CAF50")
        }
        val barData = BarData(dataSet)
        chart.data = barData
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(report.labels)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.axisLeft.axisMinimum = 0f
        chart.description.isEnabled = false
        chart.invalidate()
    }

    private fun loadFinancialReport() {
        lifecycleScope.launch {
            try {
                binding.progressFinancial.visibility = View.VISIBLE
                val token = PreferencesManager.getToken() ?: return@launch
                val dateParam = dateFormat.format(currentDate)
                val response = ApiClient.adminApiService.getFinancialReport(
                    "Bearer $token",
                    currentPeriod,
                    dateParam
                )
                if (response.isSuccessful) {
                    val report = response.body()
                    if (report != null) {
                        displayFinancialChart(report)
                        updateFinancialSummary(report)
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки финансового отчёта", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressFinancial.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun displayFinancialChart(report: FinancialReport) {
        val chart = binding.chartFinancial as LineChart
        val dataSets = mutableListOf<ILineDataSet>()
        report.chart.datasets.forEachIndexed { index, dataset ->
            val entries = mutableListOf<Entry>()
            report.chart.labels.forEachIndexed { i, _ ->
                entries.add(Entry(i.toFloat(), dataset.data[i].toFloat()))
            }
            val lineDataSet = LineDataSet(entries, dataset.label).apply {
                color = Color.parseColor(dataset.color)
                setCircleColor(Color.parseColor(dataset.color))
                lineWidth = 2f
                circleRadius = 3f
            }
            dataSets.add(lineDataSet)
        }
        val lineData = LineData(dataSets)
        chart.data = lineData
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(report.chart.labels)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.description.isEnabled = false
        chart.invalidate()
    }

    private fun updateFinancialSummary(report: FinancialReport) {
        binding.tvTotalRevenue.text = "Общая выручка: ${String.format("%.2f", report.totalRevenue)} ₽"
        binding.tvSubscriptionsRevenue.text = "Абонементы: ${String.format("%.2f", report.subscriptionsRevenue)} ₽"
        binding.tvSinglePayments.text = "Разовые: ${String.format("%.2f", report.singlePayments)} ₽"
        binding.tvTransactionsCount.text = "Транзакций: ${report.transactionsCount}"
    }

    private fun exportReport(format: String) {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val dateParam = dateFormat.format(currentDate)
                val response = ApiClient.adminApiService.exportReport(
                    "Bearer $token",
                    "full",
                    currentPeriod,
                    format
                )
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Отчёт успешно экспортирован", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Ошибка экспорта: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
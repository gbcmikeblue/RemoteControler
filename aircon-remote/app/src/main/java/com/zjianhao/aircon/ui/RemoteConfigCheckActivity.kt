package com.zjianhao.aircon.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zjianhao.aircon.R
import com.zjianhao.aircon.databinding.ActivityRemoteConfigCheckBinding
import com.zjianhao.aircon.probe.ProbeFacts
import com.zjianhao.aircon.probe.ProbeResult
import com.zjianhao.aircon.probe.RemoteConfigDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 万能遥控配置可获取性检测。
 *
 * 触发条件：
 * - 进入页面自动检测一次
 * - 点击「重新检测」手动再次触发
 *
 * 结果反馈：顶部结论 + 逐项检测明细 + 一句话 Snackbar。
 * 失败处理：展开说明卡片，解释沙箱限制与替代方案，而不是只给一个失败标记。
 */
class RemoteConfigCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteConfigCheckBinding
    private lateinit var detector: RemoteConfigDetector
    private lateinit var adapter: ProbeResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteConfigCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.fitToolbarToStatusBar(binding.toolbar)
        WindowInsetsHelper.fitBottomInset(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        detector = RemoteConfigDetector(this)
        adapter = ProbeResultAdapter()
        binding.probeList.layoutManager = LinearLayoutManager(this)
        binding.probeList.adapter = adapter

        binding.btnRecheck.setOnClickListener { runCheck() }

        runCheck()
    }

    private fun runCheck() {
        binding.btnRecheck.isEnabled = false
        binding.checking.visibility = View.VISIBLE
        binding.adviceCard.visibility = View.GONE

        lifecycleScope.launch {
            val facts = withContext(Dispatchers.IO) { detector.detect() }
            render(facts)
            binding.checking.visibility = View.GONE
            binding.btnRecheck.isEnabled = true
        }
    }

    private fun render(facts: ProbeFacts) {
        adapter.submit(buildRows(facts))

        binding.resultTitle.setText(
            if (facts.canAccessConfig) R.string.probe_ok_title else R.string.probe_fail_title
        )
        binding.resultTitle.setTextColor(
            ContextCompat.getColor(
                this,
                if (facts.canAccessConfig) R.color.probe_pass else R.color.probe_fail
            )
        )
        binding.resultSummary.text = getString(
            R.string.probe_summary,
            facts.installedApps.size,
            facts.configFileCount
        )
        binding.resultTime.text = getString(
            R.string.probe_time,
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        )

        binding.adviceCard.visibility = if (facts.canAccessConfig) View.GONE else View.VISIBLE

        snack(
            getString(
                if (facts.canAccessConfig) R.string.probe_ok_title else R.string.probe_fail_title
            )
        )
    }

    private fun buildRows(facts: ProbeFacts): List<ProbeResult> = listOf(
        ProbeResult(
            title = getString(R.string.probe_item_app),
            passed = facts.installedApps.isNotEmpty(),
            detail = if (facts.installedApps.isEmpty()) {
                getString(R.string.probe_app_none)
            } else {
                facts.installedApps.joinToString("；") { "${it.label}（${it.packageName}）" }
            }
        ),
        ProbeResult(
            title = getString(R.string.probe_item_root),
            passed = facts.hasRoot,
            detail = getString(
                if (facts.hasRoot) R.string.probe_root_ok else R.string.probe_root_none
            )
        ),
        ProbeResult(
            title = getString(R.string.probe_item_dir),
            passed = facts.readableDirs.isNotEmpty(),
            detail = if (facts.readableDirs.isEmpty()) {
                getString(R.string.probe_dir_none)
            } else {
                getString(R.string.probe_dir_ok, facts.readableDirs.joinToString("、"))
            }
        ),
        ProbeResult(
            title = getString(R.string.probe_item_file),
            passed = facts.configFileCount > 0,
            detail = getString(
                if (facts.configFileCount > 0) R.string.probe_file_ok else R.string.probe_file_none,
                facts.configFileCount
            )
        )
    )

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}

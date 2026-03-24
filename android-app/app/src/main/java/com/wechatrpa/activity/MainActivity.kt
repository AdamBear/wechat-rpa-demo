package com.wechatrpa.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wechatrpa.R
import com.wechatrpa.service.HttpServerService
import com.wechatrpa.service.RpaAccessibilityService
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var tvHttpServer: TextView
    private lateinit var tvCurrentApp: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnHttpServer: Button
    private var statusTimer: Timer? = null
    private var httpServerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        tryAutoStartHttpService()
    }

    override fun onResume() {
        super.onResume()
        startStatusRefresh()
        tryAutoStartHttpService()
    }

    override fun onPause() {
        super.onPause()
        statusTimer?.cancel()
        statusTimer = null
    }

    private fun createLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)

            addView(TextView(context).apply {
                text = getString(R.string.main_title)
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 32)
            })

            tvAccessibility = TextView(context).apply {
                text = getString(R.string.accessibility_checking)
                textSize = 16f
                setPadding(0, 16, 0, 8)
            }
            addView(tvAccessibility)

            btnAccessibility = Button(context).apply {
                text = getString(R.string.open_accessibility_settings)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            addView(btnAccessibility)

            tvHttpServer = TextView(context).apply {
                text = getString(R.string.http_server_starting)
                textSize = 16f
                setPadding(0, 24, 0, 8)
            }
            addView(tvHttpServer)

            btnHttpServer = Button(context).apply {
                text = getString(R.string.restart_http_server)
                setOnClickListener { toggleHttpServer() }
            }
            addView(btnHttpServer)

            tvCurrentApp = TextView(context).apply {
                text = getString(R.string.current_package, "-")
                textSize = 14f
                setPadding(0, 24, 0, 8)
            }
            addView(tvCurrentApp)

            tvStatus = TextView(context).apply {
                text = getString(R.string.status_preparing)
                textSize = 14f
                setPadding(0, 24, 0, 0)
                setTextColor(Color.GRAY)
            }
            addView(tvStatus)

            addView(TextView(context).apply {
                text = getString(R.string.usage_text)
                textSize = 13f
                setPadding(0, 32, 0, 0)
                setTextColor(Color.DKGRAY)
            })
        }
    }

    private fun tryAutoStartHttpService() {
        if (httpServerRunning) return
        val intent = Intent(this, HttpServerService::class.java)
        startForegroundService(intent)
        httpServerRunning = true
        btnHttpServer.text = getString(R.string.stop_http_server)
    }

    private fun toggleHttpServer() {
        if (httpServerRunning) {
            stopService(Intent(this, HttpServerService::class.java))
            httpServerRunning = false
            btnHttpServer.text = getString(R.string.start_http_server)
        } else {
            val intent = Intent(this, HttpServerService::class.java)
            startForegroundService(intent)
            httpServerRunning = true
            btnHttpServer.text = getString(R.string.stop_http_server)
        }
    }

    private fun startStatusRefresh() {
        statusTimer?.cancel()
        statusTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { updateStatus() }
                }
            }, 0, 2000)
        }
    }

    private fun updateStatus() {
        val service = RpaAccessibilityService.instance
        val accessibilityOn = service != null
        if (!httpServerRunning) {
            tryAutoStartHttpService()
        }

        tvAccessibility.text = if (accessibilityOn) {
            getString(R.string.accessibility_enabled)
        } else {
            getString(R.string.accessibility_disabled)
        }

        tvHttpServer.text = if (httpServerRunning) {
            getString(R.string.http_server_running)
        } else {
            getString(R.string.http_server_stopped)
        }

        tvCurrentApp.text = getString(R.string.current_package, service?.currentPackage ?: "-")

        tvStatus.text = if (accessibilityOn && httpServerRunning) {
            getString(R.string.status_ready)
        } else if (httpServerRunning) {
            getString(R.string.status_wait_accessibility)
        } else {
            getString(R.string.status_wait_services)
        }
    }
}

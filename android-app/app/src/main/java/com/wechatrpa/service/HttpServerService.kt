package com.wechatrpa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.wechatrpa.model.AppTarget
import com.wechatrpa.model.TaskRequest
import com.wechatrpa.model.TaskResult
import com.wechatrpa.model.TaskType
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HttpServerService : Service() {

    companion object {
        private const val TAG = "HttpServerService"
        private const val PORT = 9527
        private const val CHANNEL_ID = "rpa_http_server"
        private const val NOTIFICATION_ID = 1001
    }

    private var httpServer: RpaHttpServer? = null
    private val taskController = TaskController()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        taskController.start()
        ensureHttpServerStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand startId=$startId flags=$flags")
        ensureHttpServerStarted()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        httpServer?.stop()
        httpServer = null
        taskController.stop()
        Log.i(TAG, "HTTP server service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:HttpServer")?.apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
        if (wakeLock != null) {
            Log.i(TAG, "WakeLock acquired")
        }
    }

    @Synchronized
    private fun ensureHttpServerStarted() {
        if (httpServer != null) {
            Log.i(TAG, "HTTP server already initialized")
            return
        }

        Thread {
            try {
                val server = RpaHttpServer("0.0.0.0", PORT, taskController)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                httpServer = server
                Log.i(TAG, "HTTP server started on 0.0.0.0:$PORT")
            } catch (e: Exception) {
                httpServer = null
                Log.e(TAG, "Failed to start HTTP server: ${e.message}", e)
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RPA Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local RPA HTTP server alive"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("WeChat RPA service running")
                .setContentText("HTTP API listening on $PORT")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("WeChat RPA service running")
                .setContentText("HTTP API listening on $PORT")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }

    class RpaHttpServer(
        hostname: String,
        port: Int,
        private val taskController: TaskController
    ) : NanoHTTPD(hostname, port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            Log.i("RpaHttpServer", "serve $method $uri")

            return try {
                when {
                    uri == "/api/status" && method == Method.GET -> handleStatus()

                    uri == "/api/send_message" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleSendMessage(body)
                    }

                    uri == "/api/read_messages" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleReadMessages(body)
                    }

                    uri == "/api/create_group" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleCreateGroup(body)
                    }

                    uri == "/api/invite_to_group" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleInviteToGroup(body)
                    }

                    uri == "/api/remove_from_group" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleRemoveFromGroup(body)
                    }

                    uri == "/api/get_group_members" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleGetGroupMembers(body)
                    }

                    uri == "/api/get_contact_list" && (method == Method.GET || method == Method.POST) -> {
                        val body = if (method == Method.POST) parseBody(session) else JSONObject()
                        handleGetContactList(body)
                    }

                    uri == "/api/dump_ui" && method == Method.GET -> handleDumpUi()

                    uri == "/api/debug_set_text" && method == Method.POST -> {
                        val body = parseBody(session)
                        handleDebugSetText(body)
                    }

                    uri.startsWith("/api/task_result/") && method == Method.GET -> {
                        val taskId = uri.removePrefix("/api/task_result/")
                        handleTaskResult(taskId)
                    }

                    else -> jsonResponse(404, false, "API not found: $uri")
                }
            } catch (e: Exception) {
                Log.e("RpaHttpServer", "Request failed: ${e.message}", e)
                jsonResponse(500, false, "Internal error: ${e.message}")
            }
        }

        private fun appTargetFromBody(body: JSONObject): AppTarget {
            return if (body.optString("app_type", "wework").equals("wechat", ignoreCase = true)) {
                AppTarget.WECHAT
            } else {
                AppTarget.WEWORK
            }
        }

        private fun handleStatus(): Response {
            val service = RpaAccessibilityService.instance
            val status = JSONObject().apply {
                put("accessibility_enabled", service != null)
                put("current_package", service?.currentPackage ?: "")
                put("current_class", service?.currentClassName ?: "")
                put("task_queue_size", taskController.getQueueSize())
                put("http_server", true)
            }
            return jsonResponse(200, true, "ok", status)
        }

        private fun handleSendMessage(body: JSONObject): Response {
            val contact = body.optString("contact", "")
            val message = body.optString("message", "")
            if (contact.isBlank() || message.isBlank()) {
                return jsonResponse(400, false, "Missing params: contact, message")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.SEND_MESSAGE,
                target = appTargetFromBody(body),
                params = mapOf("contact" to contact, "message" to message)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleReadMessages(body: JSONObject): Response {
            val contact = body.optString("contact", "")
            val count = body.optInt("count", 10)
            if (contact.isBlank()) {
                return jsonResponse(400, false, "Missing params: contact")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.READ_MESSAGES,
                target = appTargetFromBody(body),
                params = mapOf("contact" to contact, "count" to count)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleCreateGroup(body: JSONObject): Response {
            val groupName = body.optString("group_name", "")
            val membersArray = body.optJSONArray("members") ?: JSONArray()
            val members = (0 until membersArray.length()).map { membersArray.getString(it) }
            if (members.isEmpty()) {
                return jsonResponse(400, false, "Missing params: members")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.CREATE_GROUP,
                target = appTargetFromBody(body),
                params = mapOf("group_name" to groupName, "members" to members)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleInviteToGroup(body: JSONObject): Response {
            val groupName = body.optString("group_name", "")
            val membersArray = body.optJSONArray("members") ?: JSONArray()
            val members = (0 until membersArray.length()).map { membersArray.getString(it) }
            if (groupName.isBlank() || members.isEmpty()) {
                return jsonResponse(400, false, "Missing params: group_name, members")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.INVITE_TO_GROUP,
                target = appTargetFromBody(body),
                params = mapOf("group_name" to groupName, "members" to members)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleRemoveFromGroup(body: JSONObject): Response {
            val groupName = body.optString("group_name", "")
            val membersArray = body.optJSONArray("members") ?: JSONArray()
            val members = (0 until membersArray.length()).map { membersArray.getString(it) }
            if (groupName.isBlank() || members.isEmpty()) {
                return jsonResponse(400, false, "Missing params: group_name, members")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.REMOVE_FROM_GROUP,
                target = appTargetFromBody(body),
                params = mapOf("group_name" to groupName, "members" to members)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleGetGroupMembers(body: JSONObject): Response {
            val groupName = body.optString("group_name", "")
            if (groupName.isBlank()) {
                return jsonResponse(400, false, "Missing params: group_name")
            }

            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.GET_GROUP_MEMBERS,
                target = appTargetFromBody(body),
                params = mapOf("group_name" to groupName)
            )
            taskController.submitTask(task)
            return jsonResponse(200, true, "queued", JSONObject().put("task_id", taskId))
        }

        private fun handleGetContactList(body: JSONObject): Response {
            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(
                taskId = taskId,
                taskType = TaskType.GET_CONTACT_LIST,
                target = appTargetFromBody(body)
            )
            taskController.submitTask(task)

            val pollIntervalMs = 1000L
            val timeoutMs = 90_000L
            var result: TaskResult? = null
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                result = taskController.getResult(taskId)
                if (result != null) {
                    break
                }
                Thread.sleep(pollIntervalMs)
            }
            if (result == null) {
                result = taskController.getResult(taskId)
            }

            val data = when (val raw = result?.data) {
                is List<*> -> JSONArray(raw.map { it.toString() })
                else -> raw
            }
            return if (result != null && result.success) {
                jsonResponse(200, true, result.message, data)
            } else {
                jsonResponse(200, result?.success == true, result?.message ?: "Failed to get contact list", data)
            }
        }

        private fun handleDumpUi(): Response {
            val taskId = UUID.randomUUID().toString().take(8)
            val task = TaskRequest(taskId = taskId, taskType = TaskType.DUMP_UI_TREE)
            taskController.submitTask(task)
            Thread.sleep(2000)

            val result = taskController.getResult(taskId)
            return if (result != null) {
                jsonResponse(200, result.success, result.message, result.data?.toString() ?: "")
            } else {
                jsonResponse(
                    200,
                    true,
                    "queued",
                    JSONObject().put("task_id", taskId)
                )
            }
        }

        private fun handleDebugSetText(body: JSONObject): Response {
            val viewId = body.optString("view_id", "")
            val text = body.optString("text", "")
            if (viewId.isBlank() || text.isBlank()) {
                return jsonResponse(400, false, "Missing params: view_id, text")
            }

            val service = RpaAccessibilityService.instance
                ?: return jsonResponse(503, false, "Accessibility service not connected")
            val node = service.findById(viewId).firstOrNull()
                ?: return jsonResponse(404, false, "Node not found: $viewId")

            return if (service.inputText(node, text)) {
                jsonResponse(200, true, "ok", JSONObject().put("view_id", viewId).put("text", text))
            } else {
                jsonResponse(500, false, "Failed to set text", JSONObject().put("view_id", viewId))
            }
        }

        private fun handleTaskResult(taskId: String): Response {
            val result = taskController.getResult(taskId)
            return if (result != null) {
                val data = JSONObject().apply {
                    put("task_id", result.taskId)
                    put("success", result.success)
                    put("message", result.message)
                    put("data", result.data?.toString() ?: "")
                }
                jsonResponse(200, true, "ok", data)
            } else {
                jsonResponse(200, false, "Task result not found")
            }
        }

        private fun parseBody(session: IHTTPSession): JSONObject {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val bodyStr = files["postData"] ?: ""
            return if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
        }

        private fun jsonResponse(
            code: Int,
            success: Boolean,
            message: String,
            data: Any? = null
        ): Response {
            val json = JSONObject().apply {
                put("code", code)
                put("success", success)
                put("message", message)
                if (data != null) {
                    put("data", data)
                }
            }

            return newFixedLengthResponse(
                Response.Status.lookup(code) ?: Response.Status.OK,
                "application/json",
                json.toString()
            )
        }
    }
}

package com.wechatrpa.service

import android.util.Log
import com.wechatrpa.model.TaskRequest
import com.wechatrpa.model.TaskResult
import com.wechatrpa.model.TaskType
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Serial task controller for device-side HTTP API requests.
 */
class TaskController {

    companion object {
        private const val TAG = "TaskController"
        private const val TASK_TIMEOUT_SECONDS = 45L
    }

    private val taskQueue = ConcurrentLinkedQueue<TaskRequest>()
    private val isRunning = AtomicBoolean(false)
    private val weworkOperator = WeworkOperator()
    private val resultMap = linkedMapOf<String, TaskResult>()

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Task controller already started")
            return
        }
        Log.i(TAG, "Task controller started")
        thread(name = "TaskExecutor", isDaemon = true) {
            while (isRunning.get()) {
                val task = taskQueue.poll()
                if (task != null) {
                    executeTask(task)
                } else {
                    Thread.sleep(200)
                }
            }
            Log.i(TAG, "Task controller stopped")
        }
    }

    fun stop() {
        isRunning.set(false)
        Log.i(TAG, "Task controller stopping...")
    }

    fun submitTask(task: TaskRequest): String {
        taskQueue.offer(task)
        Log.i(TAG, "Task queued: ${task.taskId} (${task.taskType}), queueSize=${taskQueue.size}")
        return task.taskId
    }

    fun getResult(taskId: String): TaskResult? {
        return resultMap[taskId]
    }

    fun getQueueSize(): Int = taskQueue.size

    private fun executeTask(task: TaskRequest) {
        Log.i(TAG, "Task started: ${task.taskId} (${task.taskType})")
        val startTime = System.currentTimeMillis()
        val executor = Executors.newSingleThreadExecutor()

        val future = executor.submit<TaskResult> {
            when (task.taskType) {
                TaskType.SEND_MESSAGE -> {
                    val contact = task.getString("contact")
                    val message = task.getString("message")
                    if (contact.isBlank() || message.isBlank()) {
                        TaskResult(task.taskId, false, "Missing required params: contact/message")
                    } else {
                        weworkOperator.sendMessage(contact, message, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.READ_MESSAGES -> {
                    val contact = task.getString("contact")
                    val count = task.getInt("count", 10)
                    if (contact.isBlank()) {
                        TaskResult(task.taskId, false, "Missing required param: contact")
                    } else {
                        weworkOperator.readMessagesFrom(contact, count, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.CREATE_GROUP -> {
                    val groupName = task.getString("group_name")
                    val members = task.getStringList("members")
                    if (members.isEmpty()) {
                        TaskResult(task.taskId, false, "Missing required param: members")
                    } else {
                        weworkOperator.createGroup(groupName, members, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.INVITE_TO_GROUP -> {
                    val groupName = task.getString("group_name")
                    val members = task.getStringList("members")
                    if (groupName.isBlank() || members.isEmpty()) {
                        TaskResult(task.taskId, false, "Missing required params: group_name/members")
                    } else {
                        weworkOperator.inviteToGroup(groupName, members, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.REMOVE_FROM_GROUP -> {
                    val groupName = task.getString("group_name")
                    val members = task.getStringList("members")
                    if (groupName.isBlank() || members.isEmpty()) {
                        TaskResult(task.taskId, false, "Missing required params: group_name/members")
                    } else {
                        weworkOperator.removeFromGroup(groupName, members, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.GET_GROUP_MEMBERS -> {
                    val groupName = task.getString("group_name")
                    if (groupName.isBlank()) {
                        TaskResult(task.taskId, false, "Missing required param: group_name")
                    } else {
                        weworkOperator.getGroupMembers(groupName, task.target).copy(taskId = task.taskId)
                    }
                }

                TaskType.GET_CONTACT_LIST -> {
                    weworkOperator.getContactList(task.target).copy(taskId = task.taskId)
                }

                TaskType.DUMP_UI_TREE -> {
                    val tree = weworkOperator.dumpUiTree()
                    TaskResult(task.taskId, true, "UI tree dumped", tree)
                }

                else -> {
                    TaskResult(task.taskId, false, "Unsupported task type: ${task.taskType}")
                }
            }
        }

        val result = try {
            future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            Log.e(TAG, "Task timed out: ${task.taskId} timeout=${TASK_TIMEOUT_SECONDS}s")
            TaskResult(task.taskId, false, "Task timed out after ${TASK_TIMEOUT_SECONDS}s")
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${e.message}", e)
            TaskResult(task.taskId, false, "Task execution failed: ${e.message}")
        } finally {
            executor.shutdownNow()
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Task finished: ${task.taskId} success=${result.success} elapsed=${elapsed}ms")

        resultMap[task.taskId] = result
        while (resultMap.size > 1000) {
            val oldest = resultMap.keys.firstOrNull() ?: break
            resultMap.remove(oldest)
        }
    }
}

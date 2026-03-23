package com.wechatrpa.utils

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.wechatrpa.model.AppTarget
import com.wechatrpa.service.RpaAccessibilityService

object NodeHelper {

    private const val TAG = "NodeHelper"

    private val service: RpaAccessibilityService?
        get() = RpaAccessibilityService.instance

    fun findFirstById(resourceId: String): AccessibilityNodeInfo? {
        return service?.findById(resourceId)?.firstOrNull()
    }

    fun findByExactText(text: String): AccessibilityNodeInfo? {
        return service?.findByExactText(text)
    }

    fun findByContainsText(text: String): List<AccessibilityNodeInfo> {
        return service?.findByText(text) ?: emptyList()
    }

    fun findAllByExactText(text: String): List<AccessibilityNodeInfo> {
        return findByContainsText(text).filter { it.text?.toString() == text }
    }

    fun findByClassName(className: String): List<AccessibilityNodeInfo> {
        return service?.findAllNodes { it.className?.toString() == className } ?: emptyList()
    }

    fun findEditTexts(): List<AccessibilityNodeInfo> {
        return findByClassName("android.widget.EditText")
    }

    fun findClickables(): List<AccessibilityNodeInfo> {
        return service?.findAllNodes { it.isClickable } ?: emptyList()
    }

    fun findScrollables(): List<AccessibilityNodeInfo> {
        return service?.findAllNodes { it.isScrollable } ?: emptyList()
    }

    fun clickText(text: String, exact: Boolean = true, preferBottomMost: Boolean = false): Boolean {
        val candidates = if (exact) findAllByExactText(text) else findByContainsText(text)
        if (candidates.isEmpty()) {
            Log.w(TAG, "clickText: not found '$text'")
            return false
        }

        val ordered = if (preferBottomMost) {
            candidates.sortedByDescending { node ->
                Rect().also { node.getBoundsInScreen(it) }.centerY()
            }
        } else {
            candidates
        }

        for (node in ordered) {
            if (service?.clickNode(node) == true) return true
        }
        return false
    }

    fun clickAnyText(
        texts: Collection<String>,
        exact: Boolean = true,
        preferBottomMost: Boolean = false
    ): Boolean {
        for (text in texts) {
            if (clickText(text, exact = exact, preferBottomMost = preferBottomMost)) {
                return true
            }
        }
        return false
    }

    fun clickId(resourceId: String): Boolean {
        val node = findFirstById(resourceId)
        if (node == null) {
            Log.w(TAG, "clickId: not found '$resourceId'")
            return false
        }
        return service?.clickNode(node) ?: false
    }

    fun clickAnyId(resourceIds: Collection<String>): Boolean {
        for (resourceId in resourceIds) {
            if (clickId(resourceId)) {
                return true
            }
        }
        return false
    }

    fun clickContentDesc(desc: String): Boolean {
        val node = service?.findAllNodes {
            it.contentDescription?.toString()?.contains(desc) == true
        }?.firstOrNull()
        if (node == null) {
            Log.w(TAG, "clickContentDesc: not found '$desc'")
            return false
        }
        return service?.clickNode(node) ?: false
    }

    fun inputToField(text: String, resourceId: String? = null): Boolean {
        val node = if (resourceId != null) {
            findFirstById(resourceId)
        } else {
            findEditTexts().firstOrNull()
        }
        if (node == null) {
            Log.w(TAG, "inputToField: no editable field")
            return false
        }
        return service?.inputText(node, text) ?: false
    }

    fun scrollAndFindText(text: String, maxScrolls: Int = 10): AccessibilityNodeInfo? {
        for (i in 0..maxScrolls) {
            val node = findByExactText(text)
            if (node != null) return node

            val scrollable = findScrollables().firstOrNull() ?: break
            service?.scrollForward(scrollable) ?: break
            Thread.sleep(800)
        }
        return null
    }

    fun waitAndClick(
        text: String? = null,
        resourceId: String? = null,
        timeoutMs: Long = 10000,
        intervalMs: Long = 500
    ): Boolean {
        val s = service ?: return false
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val node = when {
                text != null -> s.findByExactText(text)
                resourceId != null -> s.findById(resourceId).firstOrNull()
                else -> null
            }
            if (node != null) {
                return s.clickNode(node)
            }
            Thread.sleep(intervalMs)
        }
        Log.w(TAG, "waitAndClick timeout: text=$text, id=$resourceId")
        return false
    }

    fun pageContainsText(text: String): Boolean {
        return findByContainsText(text).isNotEmpty()
    }

    fun countExactTexts(texts: Collection<String>): Int {
        return texts.count { findAllByExactText(it).isNotEmpty() }
    }

    fun isInChatPage(): Boolean {
        return findByContainsText("发送").isNotEmpty() || findEditTexts().isNotEmpty()
    }

    fun isInMainPage(target: AppTarget = AppTarget.WEWORK): Boolean {
        return when (target) {
            AppTarget.WECHAT ->
                findByExactText("通讯录") != null &&
                    (findByExactText("发现") != null || findByExactText("我") != null)

            AppTarget.WEWORK ->
                countExactTexts(listOf("消息", "邮件", "文档", "工作台", "通讯录")) >= 3
        }
    }
}

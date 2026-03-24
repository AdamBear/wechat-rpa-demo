package com.wechatrpa.service

import android.content.Intent
import android.util.Log
import com.wechatrpa.model.AppTarget
import com.wechatrpa.model.ChatMessage
import com.wechatrpa.model.TaskResult
import com.wechatrpa.utils.NodeHelper

class WeworkOperator {

    companion object {
        private const val TAG = "WeworkOperator"

        private const val DELAY_SHORT = 500L
        private const val DELAY_MEDIUM = 1000L
        private const val DELAY_LONG = 2000L
        private const val DELAY_PAGE_LOAD = 3000L
        private const val DELAY_CONTACTS_LOAD = 5000L
    }

    private val service: RpaAccessibilityService?
        get() = RpaAccessibilityService.instance

    object WeworkIds {
        const val TAB_CONTACTS = "com.tencent.wework:id/hn7"
        const val SEARCH_BTN = "com.tencent.wework:id/hfp"
        const val SEARCH_ENTRY = "com.tencent.wework:id/fnp"
        const val SEARCH_ENTRY_LABEL = "com.tencent.wework:id/fno"
        const val SEARCH_INPUT = "com.tencent.wework:id/cd7"

        const val CHAT_INPUT = "com.tencent.wework:id/b4o"
        const val CHAT_SEND_BTN = "com.tencent.wework:id/b5d"
        const val CHAT_MSG_TEXT = "com.tencent.wework:id/aum"
        const val CHAT_MORE_BTN = "com.tencent.wework:id/amw"

        const val GROUP_MEMBER_ADD = "com.tencent.wework:id/c0g"
        const val GROUP_MEMBER_DEL = "com.tencent.wework:id/c0h"
        const val GROUP_NAME = "com.tencent.wework:id/bz8"
    }

    private val weworkBottomTabs = listOf(
        "\u6d88\u606f",
        "\u90ae\u4ef6",
        "\u6587\u6863",
        "\u5de5\u4f5c\u53f0",
        "\u901a\u8baf\u5f55"
    )

    private val weworkContactMarkers = listOf(
        "\u6211\u7684\u5ba2\u6237",
        "\u6211\u7684\u5ba2\u6237\u7fa4",
        "\u5916\u90e8\u8054\u7cfb\u4eba",
        "\u90e8\u95e8",
        "\u6211\u7684\u7fa4",
        "\u9080\u8bf7\u540c\u4e8b",
        "\u6dfb\u52a0\u6210\u5458",
        "\u4f01\u4e1a\u5fae\u4fe1\u901a\u8baf\u5f55",
        "\u516c\u53f8",
        "\u7ec4\u7ec7\u67b6\u6784",
        "\u53d1\u8d77/\u7ba1\u7406\u4f01\u4e1a"
    )

    private val weworkContactMarkerIds = listOf(
        "com.tencent.wework:id/kvt",
        "com.tencent.wework:id/i1m",
        "com.tencent.wework:id/hz9"
    )

    fun launchApp(target: AppTarget): Boolean {
        return try {
            val ctx = service?.applicationContext ?: return false
            val intent = ctx.packageManager.getLaunchIntentForPackage(target.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            Thread.sleep(DELAY_PAGE_LOAD)
            Log.i(TAG, "launchApp success: ${target.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed: ${e.message}")
            false
        }
    }

    fun launchWework(): Boolean = launchApp(AppTarget.WEWORK)

    fun goToMainPage(target: AppTarget = AppTarget.WEWORK): Boolean {
        Log.i(TAG, "goToMainPage: start target=${target.packageName}")
        for (i in 0..5) {
            if (NodeHelper.isInMainPage(target)) {
                Log.i(TAG, "goToMainPage: already in main page")
                return true
            }
            Log.i(TAG, "goToMainPage: pressBack round=${i + 1}")
            service?.pressBack()
            Thread.sleep(DELAY_SHORT)
        }

        if (!launchApp(target)) {
            Log.e(TAG, "goToMainPage: launchApp failed")
            return false
        }

        if (target == AppTarget.WECHAT) {
            for (i in 0..10) {
                Thread.sleep(500)
                val inMain = NodeHelper.isInMainPage(target)
                Log.i(TAG, "goToMainPage: wechat wait round=${i + 1} inMain=$inMain")
                if (inMain) return true
            }
            Thread.sleep(2000)
        } else {
            for (i in 0..12) {
                val inMain = NodeHelper.isInMainPage(target)
                Log.i(
                    TAG,
                    "goToMainPage: wework wait round=${i + 1} inMain=$inMain currentPackage=${service?.currentPackage}"
                )
                if (inMain) return true
                Thread.sleep(1000)
            }
        }

        val result = NodeHelper.isInMainPage(target)
        Log.i(TAG, "goToMainPage: finalResult=$result currentPackage=${service?.currentPackage}")
        return result
    }

    private fun isInWeworkMainShell(): Boolean {
        return service?.currentPackage == AppTarget.WEWORK.packageName &&
            NodeHelper.countExactTexts(weworkBottomTabs) >= 3
    }

    private fun isInWeworkContactsPage(): Boolean {
        if (!isInWeworkMainShell()) return false
        if (NodeHelper.findAllByExactText("\u901a\u8baf\u5f55").size >= 2) return true
        if (weworkContactMarkerIds.any { NodeHelper.findFirstById(it) != null }) return true
        return weworkContactMarkers.any { NodeHelper.pageContainsText(it) }
    }

    private fun enterWeworkContactsPage(): Boolean {
        Log.i(TAG, "enterWeworkContactsPage: start")
        if (isInWeworkContactsPage()) {
            Log.i(TAG, "enterWeworkContactsPage: already in contacts page")
            return true
        }

        val clickAttempts = listOf(
            "bottom-tab-text" to { NodeHelper.clickText("\u901a\u8baf\u5f55", preferBottomMost = true) },
            "bottom-tab-any-text" to {
                NodeHelper.clickAnyText(listOf("\u901a\u8baf\u5f55", "\u8054\u7cfb\u4eba"), preferBottomMost = true)
            },
            "bottom-tab-id" to { NodeHelper.clickId(WeworkIds.TAB_CONTACTS) },
            "wait-and-click-text" to { service?.waitAndClickText("\u901a\u8baf\u5f55", 3000) == true }
        )

        repeat(4) { round ->
            Log.i(TAG, "enterWeworkContactsPage: round=${round + 1}")
            for ((name, attempt) in clickAttempts) {
                val clicked = attempt()
                Log.i(TAG, "enterWeworkContactsPage: attempt=$name clicked=$clicked")
                if (clicked) {
                    Thread.sleep(1500)
                    val inContacts = isInWeworkContactsPage()
                    Log.i(TAG, "enterWeworkContactsPage: attempt=$name inContacts=$inContacts")
                    if (inContacts) return true
                }
            }
            Thread.sleep(500)
        }

        val result = isInWeworkContactsPage()
        Log.i(TAG, "enterWeworkContactsPage: finalResult=$result")
        return result
    }

    private fun openWeworkSearch(): Boolean {
        val attempts = listOf(
            { NodeHelper.clickAnyId(listOf(WeworkIds.SEARCH_BTN, WeworkIds.SEARCH_ENTRY, WeworkIds.SEARCH_ENTRY_LABEL)) },
            { NodeHelper.clickText("\u641c\u7d22", exact = true) },
            { NodeHelper.clickText("\u641c\u7d22", exact = false) }
        )

        repeat(3) {
            for (attempt in attempts) {
                if (attempt()) {
                    Thread.sleep(DELAY_MEDIUM)
                    return true
                }
            }
            if (enterWeworkContactsPage()) {
                Thread.sleep(DELAY_SHORT)
            }
        }

        return false
    }

    private fun clickSearchResult(contactName: String): Boolean {
        val exactNode = NodeHelper.scrollAndFindText(contactName, maxScrolls = 3)
        if (exactNode != null) {
            service?.clickNode(exactNode)
            Thread.sleep(DELAY_PAGE_LOAD)
            return true
        }

        val fuzzyNode = NodeHelper.findByContainsText(contactName)
            .firstOrNull { it.text?.toString()?.contains(contactName) == true }
        if (fuzzyNode != null) {
            service?.clickNode(fuzzyNode)
            Thread.sleep(DELAY_PAGE_LOAD)
            return true
        }

        return false
    }

    private fun findContactInCurrentList(contactName: String, maxScrolls: Int = 5): Boolean {
        Log.i(TAG, "findContactInCurrentList: start contact=$contactName maxScrolls=$maxScrolls")
        for (i in 0..maxScrolls) {
            Log.i(TAG, "findContactInCurrentList: round=${i + 1}")
            val exactNode = NodeHelper.findByExactText(contactName)
            if (exactNode != null) {
                Log.i(TAG, "findContactInCurrentList: exact match found")
                val clicked = service?.clickNode(exactNode) == true
                Log.i(TAG, "findContactInCurrentList: exact match clicked=$clicked")
                Thread.sleep(DELAY_PAGE_LOAD)
                return true
            }

            val fuzzyNode = NodeHelper.findByContainsText(contactName)
                .firstOrNull { it.text?.toString()?.contains(contactName) == true }
            if (fuzzyNode != null) {
                Log.i(TAG, "findContactInCurrentList: fuzzy match found text=${fuzzyNode.text}")
                val clicked = service?.clickNode(fuzzyNode) == true
                Log.i(TAG, "findContactInCurrentList: fuzzy match clicked=$clicked")
                Thread.sleep(DELAY_PAGE_LOAD)
                return true
            }

            val scrollable = NodeHelper.findScrollables().firstOrNull()
            if (scrollable == null) {
                Log.w(TAG, "findContactInCurrentList: no scrollable node, stop searching")
                break
            }
            val scrolled = service?.scrollForward(scrollable) == true
            Log.i(TAG, "findContactInCurrentList: scrollForward=$scrolled")
            Thread.sleep(800)
        }

        Log.w(TAG, "findContactInCurrentList: contact not found contact=$contactName")
        return false
    }

    private fun isChatWindowReady(): Boolean {
        return NodeHelper.findEditTexts().isNotEmpty() ||
            NodeHelper.pageContainsText("\u53d1\u9001") ||
            NodeHelper.findFirstById(WeworkIds.CHAT_INPUT) != null
    }

    private fun enterChatFromContactProfile(): Boolean {
        Log.i(TAG, "enterChatFromContactProfile: start")
        if (isChatWindowReady()) {
            Log.i(TAG, "enterChatFromContactProfile: chat already ready")
            return true
        }

        val attempts = listOf(
            "exact-message-entry" to {
                NodeHelper.clickAnyText(listOf("\u53d1\u6d88\u606f", "\u53d1\u9001\u6d88\u606f", "\u6d88\u606f"), exact = true)
            },
            "fuzzy-message-entry" to {
                NodeHelper.clickAnyText(listOf("\u53d1\u6d88\u606f", "\u53d1\u9001\u6d88\u606f", "\u6d88\u606f"), exact = false)
            }
        )

        repeat(3) { round ->
            Log.i(TAG, "enterChatFromContactProfile: round=${round + 1}")
            for ((name, attempt) in attempts) {
                val clicked = attempt()
                Log.i(TAG, "enterChatFromContactProfile: attempt=$name clicked=$clicked")
                if (clicked) {
                    Thread.sleep(DELAY_PAGE_LOAD)
                    val ready = isChatWindowReady()
                    Log.i(TAG, "enterChatFromContactProfile: attempt=$name ready=$ready")
                    if (ready) return true
                }
            }
            Thread.sleep(DELAY_SHORT)
        }

        val result = isChatWindowReady()
        Log.i(TAG, "enterChatFromContactProfile: finalResult=$result")
        return result
    }

    fun openChat(contactName: String, target: AppTarget = AppTarget.WEWORK): Boolean {
        Log.i(TAG, "openChat: $contactName (${target.packageName})")
        if (!goToMainPage(target)) {
            Log.e(TAG, "openChat failed: goToMainPage returned false")
            return false
        }
        Thread.sleep(DELAY_SHORT)

        if (target == AppTarget.WECHAT) {
            val searchOk = NodeHelper.clickText("\u641c\u7d22") || NodeHelper.clickId(WeworkIds.SEARCH_BTN)
            if (!searchOk) {
                Log.e(TAG, "openChat failed: cannot tap search")
                return false
            }
            Thread.sleep(DELAY_MEDIUM)
            val inputOk = NodeHelper.inputToField(contactName, WeworkIds.SEARCH_INPUT) || NodeHelper.inputToField(contactName)
            if (!inputOk) {
                Log.e(TAG, "openChat failed: cannot input search text")
                service?.pressBack()
                return false
            }
            Thread.sleep(DELAY_LONG)
            if (clickSearchResult(contactName)) return true
            service?.pressBack()
            service?.pressBack()
            return false
        }

        Log.i(TAG, "openChat: trying contacts-first path")
        if (enterWeworkContactsPage()) {
            Log.i(TAG, "openChat: contacts-first entered contacts page")
            if (findContactInCurrentList(contactName, maxScrolls = 6)) {
                Log.i(TAG, "openChat: contacts-first opened contact profile")
                if (!enterChatFromContactProfile()) {
                    Log.w(TAG, "contacts profile opened but chat page not ready")
                }
                Log.i(TAG, "openChat success via contacts-first: $contactName")
                return isChatWindowReady()
            }
            Log.w(TAG, "openChat: contacts-first could not find contact=$contactName")
            if (!goToMainPage(target)) {
                Log.e(TAG, "openChat failed: unable to return to main page after contacts-first fallback")
                return false
            }
            Thread.sleep(DELAY_SHORT)
        } else {
            Log.w(TAG, "openChat: contacts-first could not enter contacts page")
        }

        Log.i(TAG, "openChat: trying search path")
        if (openWeworkSearch()) {
            val inputOk = NodeHelper.inputToField(contactName, WeworkIds.SEARCH_INPUT) || NodeHelper.inputToField(contactName)
            Log.i(TAG, "openChat: search path inputOk=$inputOk")
            if (inputOk) {
                Thread.sleep(DELAY_LONG)
                if (clickSearchResult(contactName)) {
                    Log.i(TAG, "openChat: search path clicked search result")
                    if (!enterChatFromContactProfile()) {
                        Log.w(TAG, "search result opened but chat page not ready")
                    }
                    Log.i(TAG, "openChat success via search: $contactName")
                    return isChatWindowReady()
                }
                Log.w(TAG, "openChat: search path could not click result for $contactName")
            } else {
                Log.w(TAG, "openChat search opened but input failed")
            }
            service?.pressBack()
            Thread.sleep(DELAY_SHORT)
        } else {
            Log.w(TAG, "openChat: search path could not be opened")
        }

        if (!goToMainPage(target)) {
            Log.e(TAG, "openChat failed: unable to return to main page before contacts fallback")
            return false
        }
        Thread.sleep(DELAY_SHORT)

        Log.i(TAG, "openChat: trying final contacts fallback")
        if (enterWeworkContactsPage()) {
            if (findContactInCurrentList(contactName, maxScrolls = 6)) {
                Log.i(TAG, "openChat: final contacts fallback opened contact profile")
                if (!enterChatFromContactProfile()) {
                    Log.w(TAG, "contacts profile opened but chat page not ready")
                }
                Log.i(TAG, "openChat success via contacts: $contactName")
                return isChatWindowReady()
            }
            Log.w(TAG, "openChat: final contacts fallback could not find contact=$contactName")
        } else {
            Log.w(TAG, "openChat: final contacts fallback could not enter contacts page")
        }

        Log.e(TAG, "openChat failed: $contactName")
        service?.pressBack()
        return false
    }

    fun sendMessage(contactName: String, message: String, target: AppTarget = AppTarget.WEWORK): TaskResult {
        Log.i(TAG, "sendMessage: $contactName")
        if (!openChat(contactName, target)) {
            return TaskResult("", false, "Unable to open chat for '$contactName'")
        }

        val inputOk = if (target == AppTarget.WECHAT) {
            NodeHelper.inputToField(message) || NodeHelper.inputToField(message, WeworkIds.CHAT_INPUT)
        } else {
            NodeHelper.inputToField(message, WeworkIds.CHAT_INPUT) || NodeHelper.inputToField(message)
        }
        if (!inputOk) {
            return TaskResult("", false, "Message input failed")
        }
        Thread.sleep(DELAY_SHORT)

        val sendOk = if (target == AppTarget.WECHAT) {
            NodeHelper.clickText("\u53d1\u9001") ||
                NodeHelper.clickContentDesc("\u53d1\u9001") ||
                NodeHelper.clickId(WeworkIds.CHAT_SEND_BTN)
        } else {
            NodeHelper.clickId(WeworkIds.CHAT_SEND_BTN) ||
                NodeHelper.clickText("\u53d1\u9001") ||
                NodeHelper.clickContentDesc("\u53d1\u9001")
        }
        if (!sendOk) {
            return TaskResult("", false, "Send button tap failed")
        }
        Thread.sleep(DELAY_SHORT)

        return TaskResult("", true, "Message sent successfully")
    }

    fun sendInCurrentChat(message: String): Boolean {
        if (!NodeHelper.inputToField(message, WeworkIds.CHAT_INPUT)) {
            if (!NodeHelper.inputToField(message)) return false
        }
        Thread.sleep(DELAY_SHORT)

        if (!NodeHelper.clickId(WeworkIds.CHAT_SEND_BTN)) {
            if (!NodeHelper.clickText("\u53d1\u9001")) return false
        }
        return true
    }

    fun readMessages(count: Int = 10): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val msgNodes = service?.findById(WeworkIds.CHAT_MSG_TEXT) ?: emptyList()
        for (node in msgNodes.takeLast(count)) {
            val text = node.text?.toString() ?: continue
            if (text.isNotBlank()) {
                messages.add(ChatMessage(content = text, msgType = "text"))
            }
        }

        if (messages.isEmpty()) {
            val textViews = NodeHelper.findByClassName("android.widget.TextView")
            val excludeTexts = setOf(
                "\u53d1\u9001",
                "\u6d88\u606f",
                "\u901a\u8baf\u5f55",
                "\u5de5\u4f5c\u53f0",
                "\u6211",
                "\u6309\u4f4f \u8bf4\u8bdd",
                "\u66f4\u591a",
                "\u8fd4\u56de"
            )
            for (tv in textViews.takeLast(count * 2)) {
                val text = tv.text?.toString() ?: continue
                if (text.isNotBlank() && text.length > 1 && text !in excludeTexts) {
                    messages.add(ChatMessage(content = text, msgType = "text"))
                }
            }
        }

        Log.i(TAG, "readMessages count=${messages.size}")
        return messages.takeLast(count)
    }

    fun readMessagesFrom(contactName: String, count: Int = 10, target: AppTarget = AppTarget.WEWORK): TaskResult {
        if (!openChat(contactName, target)) {
            return TaskResult("", false, "Unable to open chat")
        }
        val messages = readMessages(count)
        return TaskResult("", true, "Read messages successfully", messages)
    }

    fun createGroup(groupName: String, members: List<String>, target: AppTarget = AppTarget.WEWORK): TaskResult {
        Log.i(TAG, "createGroup: $groupName")
        goToMainPage(target)
        Thread.sleep(DELAY_SHORT)

        if (!NodeHelper.clickText("+") && !NodeHelper.clickId("com.tencent.wework:id/hfq")) {
            return TaskResult("", false, "Unable to tap create button")
        }
        Thread.sleep(DELAY_MEDIUM)

        if (!NodeHelper.clickText("\u53d1\u8d77\u7fa4\u804a")) {
            return TaskResult("", false, "Unable to find create group entry")
        }
        Thread.sleep(DELAY_PAGE_LOAD)

        for (member in members) {
            val searchInput = NodeHelper.findEditTexts().firstOrNull()
            if (searchInput != null) {
                service?.inputText(searchInput, member)
                Thread.sleep(DELAY_LONG)

                val memberNode = NodeHelper.findByExactText(member)
                if (memberNode != null) {
                    service?.clickNode(memberNode)
                    Thread.sleep(DELAY_SHORT)
                    service?.clearText(searchInput)
                    Thread.sleep(DELAY_SHORT)
                } else {
                    Log.w(TAG, "createGroup member not found: $member")
                }
            }
        }

        if (!NodeHelper.clickText("\u786e\u5b9a") && !NodeHelper.clickText("\u5b8c\u6210")) {
            return TaskResult("", false, "Unable to confirm group creation")
        }
        Thread.sleep(DELAY_PAGE_LOAD)

        if (groupName.isNotBlank()) {
            modifyGroupName(groupName)
        }

        return TaskResult("", true, "Group created successfully")
    }

    fun inviteToGroup(groupName: String, members: List<String>, target: AppTarget = AppTarget.WEWORK): TaskResult {
        Log.i(TAG, "inviteToGroup: $groupName")

        if (!openChat(groupName, target)) {
            return TaskResult("", false, "Unable to open group: $groupName")
        }
        if (!NodeHelper.clickId(WeworkIds.CHAT_MORE_BTN)) {
            return TaskResult("", false, "Unable to open group settings")
        }
        Thread.sleep(DELAY_PAGE_LOAD)

        if (!NodeHelper.clickId(WeworkIds.GROUP_MEMBER_ADD)) {
            val addBtn = service?.findNode { it.contentDescription?.toString()?.contains("\u6dfb\u52a0") == true }
            if (addBtn != null) {
                service?.clickNode(addBtn)
            } else {
                return TaskResult("", false, "Unable to find add member button")
            }
        }
        Thread.sleep(DELAY_PAGE_LOAD)

        val successList = mutableListOf<String>()
        val failList = mutableListOf<String>()

        for (member in members) {
            val searchInput = NodeHelper.findEditTexts().firstOrNull()
            if (searchInput != null) {
                service?.inputText(searchInput, member)
                Thread.sleep(DELAY_LONG)

                val memberNode = NodeHelper.findByExactText(member)
                if (memberNode != null) {
                    service?.clickNode(memberNode)
                    successList.add(member)
                    Thread.sleep(DELAY_SHORT)
                    service?.clearText(searchInput)
                    Thread.sleep(DELAY_SHORT)
                } else {
                    failList.add(member)
                    service?.clearText(searchInput)
                    Thread.sleep(DELAY_SHORT)
                }
            }
        }

        if (successList.isNotEmpty()) {
            NodeHelper.clickText("\u786e\u5b9a") || NodeHelper.clickText("\u9080\u8bf7")
            Thread.sleep(DELAY_PAGE_LOAD)
        }

        val result = mapOf("success" to successList, "failed" to failList)
        return TaskResult("", true, "Invite finished", result)
    }

    fun removeFromGroup(groupName: String, members: List<String>, target: AppTarget = AppTarget.WEWORK): TaskResult {
        Log.i(TAG, "removeFromGroup: $groupName")

        if (!openChat(groupName, target)) {
            return TaskResult("", false, "Unable to open group")
        }
        NodeHelper.clickId(WeworkIds.CHAT_MORE_BTN)
        Thread.sleep(DELAY_PAGE_LOAD)

        if (!NodeHelper.clickId(WeworkIds.GROUP_MEMBER_DEL)) {
            return TaskResult("", false, "Unable to find remove member button")
        }
        Thread.sleep(DELAY_PAGE_LOAD)

        val removedList = mutableListOf<String>()
        for (member in members) {
            val searchInput = NodeHelper.findEditTexts().firstOrNull()
            if (searchInput != null) {
                service?.inputText(searchInput, member)
                Thread.sleep(DELAY_LONG)

                val memberNode = NodeHelper.findByExactText(member)
                if (memberNode != null) {
                    service?.clickNode(memberNode)
                    removedList.add(member)
                    Thread.sleep(DELAY_SHORT)
                    service?.clearText(searchInput)
                }
            }
        }

        if (removedList.isNotEmpty()) {
            NodeHelper.clickText("\u786e\u5b9a") || NodeHelper.clickText("\u5220\u9664")
            Thread.sleep(DELAY_MEDIUM)
            NodeHelper.clickText("\u786e\u8ba4") || NodeHelper.clickText("\u786e\u5b9a")
            Thread.sleep(DELAY_PAGE_LOAD)
        }

        return TaskResult("", true, "Remove finished", removedList)
    }

    fun getGroupMembers(groupName: String, target: AppTarget = AppTarget.WEWORK): TaskResult {
        if (!openChat(groupName, target)) {
            return TaskResult("", false, "Unable to open group")
        }
        NodeHelper.clickId(WeworkIds.CHAT_MORE_BTN)
        Thread.sleep(DELAY_PAGE_LOAD)

        val members = mutableListOf<String>()
        val textViews = NodeHelper.findByClassName("android.widget.TextView")
        val excludeTexts = setOf(
            "\u7fa4\u804a\u540d\u79f0",
            "\u7fa4\u516c\u544a",
            "\u7fa4\u7ba1\u7406",
            "\u6295\u8bc9",
            "\u6dfb\u52a0",
            "\u5220\u9664",
            "\u5168\u90e8\u7fa4\u6210\u5458",
            "\u67e5\u770b\u5168\u90e8",
            "\u7fa4\u6210\u5458",
            "\u6d88\u606f\u514d\u6253\u6270",
            "\u7f6e\u9876\u804a\u5929",
            "\u4fdd\u5b58\u5230\u901a\u8baf\u5f55"
        )

        for (tv in textViews) {
            val text = tv.text?.toString() ?: continue
            if (text.isNotBlank() && text.length in 2..20 && text !in excludeTexts) {
                members.add(text)
            }
        }

        service?.pressBack()
        return TaskResult("", true, "Get group members successfully", members.distinct())
    }

    private fun modifyGroupName(newName: String): Boolean {
        val nameNode = NodeHelper.findFirstById(WeworkIds.GROUP_NAME)
            ?: NodeHelper.findByExactText("\u7fa4\u804a")
        if (nameNode != null) {
            service?.clickNode(nameNode)
            Thread.sleep(DELAY_MEDIUM)

            val editText = NodeHelper.findEditTexts().firstOrNull()
            if (editText != null) {
                service?.inputText(editText, newName)
                Thread.sleep(DELAY_SHORT)
                NodeHelper.clickText("\u786e\u5b9a") || NodeHelper.clickText("\u4fdd\u5b58")
                Thread.sleep(DELAY_MEDIUM)
                return true
            }
        }
        return false
    }

    fun getContactList(target: AppTarget = AppTarget.WEWORK): TaskResult {
        Log.i(TAG, "getContactList target=${target.packageName}")
        if (!goToMainPage(target)) {
            return TaskResult("", false, "Unable to reach main page")
        }
        Thread.sleep(DELAY_MEDIUM)

        if (target == AppTarget.WEWORK) {
            if (!enterWeworkContactsPage()) {
                return TaskResult("", false, "Unable to enter contacts page")
            }
        } else {
            val entered = service?.waitAndClickText("\u901a\u8baf\u5f55", 15000) == true ||
                NodeHelper.clickText("\u901a\u8baf\u5f55", preferBottomMost = true)
            if (!entered) {
                return TaskResult("", false, "Unable to enter contacts page")
            }
        }
        Thread.sleep(DELAY_CONTACTS_LOAD)

        if (target == AppTarget.WEWORK && !isInWeworkContactsPage()) {
            return TaskResult("", false, "Contacts page markers not detected")
        }

        val exclude = setOf(
            "\u901a\u8baf\u5f55",
            "\u6d88\u606f",
            "\u90ae\u4ef6",
            "\u6587\u6863",
            "\u5de5\u4f5c\u53f0",
            "\u6211",
            "\u5fae\u4fe1",
            "\u641c\u7d22",
            "\u6dfb\u52a0",
            "\u65b0\u7684\u670b\u53cb",
            "\u7fa4\u804a",
            "\u6807\u7b7e",
            "\u516c\u4f17\u53f7",
            "\u4f01\u4e1a\u5fae\u4fe1",
            "\u5168\u90e8",
            "\u7ec4\u7ec7",
            "\u7ba1\u7406\u5458",
            "\u89c6\u9891\u53f7",
            "\u5c0f\u7a0b\u5e8f",
            "\u670b\u53cb\u5708",
            "\u626b\u4e00\u626b",
            "\u770b\u4e00\u770b",
            "\u6211\u7684\u5ba2\u6237",
            "\u6211\u7684\u5ba2\u6237\u7fa4",
            "\u5916\u90e8\u8054\u7cfb\u4eba",
            "\u90e8\u95e8",
            "\u6211\u7684\u7fa4"
        )

        val names = mutableSetOf<String>()
        val maxScrolls = 5
        for (scrollRound in 0..maxScrolls) {
            val textViews = NodeHelper.findByClassName("android.widget.TextView")
            for (tv in textViews) {
                val text = tv.text?.toString()?.trim() ?: continue
                if (
                    text.length in 2..30 &&
                    text !in exclude &&
                    !text.all { it.isDigit() } &&
                    !text.contains("@") &&
                    !text.contains("http", ignoreCase = true)
                ) {
                    names.add(text)
                }
            }

            if (scrollRound < maxScrolls) {
                val scrollable = NodeHelper.findScrollables().firstOrNull()
                if (scrollable != null) {
                    service?.scrollForward(scrollable)
                    Thread.sleep(800)
                } else {
                    break
                }
            }
        }

        val list = names.sorted()
        return TaskResult("", true, "Collected ${list.size} contacts", list)
    }

    fun dumpUiTree(): String {
        return service?.dumpNodeTree() ?: "AccessibilityService not connected"
    }
}

package com.luoshui.paycardeditor.data

import com.luoshui.paycardeditor.app.App
import com.luoshui.paycardeditor.core.HookEnvironment
import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.model.CardSnapshotState
import com.luoshui.paycardeditor.model.HomeState
import com.luoshui.paycardeditor.model.ModuleStatusLevel
import com.luoshui.paycardeditor.model.ModuleStatusState
import com.luoshui.paycardeditor.model.TroubleshootState


import android.content.Context
import io.github.libxposed.service.XposedService
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ModuleStateRepository {

    fun loadHomeState(): HomeState {
        val moduleStatus = loadModuleStatus()
        var cardState = loadCardState()
        if (cardState.cards.isEmpty() && cardState.warning.isBlank()) {
            cardState = cardState.copy(
                warning = when (moduleStatus.level) {
                    ModuleStatusLevel.ACTIVE -> "已收到 Hook 初始化上报，但还没有卡片快照。先打开一次小米钱包卡片页，再回到这里刷新。"
                    ModuleStatusLevel.WAITING -> "尚未检测到 Hook 上报。打开一次小米钱包卡片页，再回到这里刷新。"
                    ModuleStatusLevel.INACTIVE -> "先在框架里勾选 com.miui.tsmclient 作用域并重启目标进程，Hook 才会开始上报卡片信息。"
                }
            )
        }
        return HomeState(moduleStatus = moduleStatus, cardState = cardState)
    }

    fun loadTroubleshootState(): TroubleshootState {
        val context = App.appContext
        val prefs = context?.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        val debugInfo = prefs?.getString(HookEnvironment.PREF_KEY_DEBUG_STATUS, "").orEmpty()
        val hookMethods = prefs?.getString(HookEnvironment.PREF_KEY_HOOK_METHODS, "").orEmpty()
        val updatedAt = prefs?.getLong(HookEnvironment.PREF_KEY_TROUBLESHOOT_UPDATED_AT, 0L) ?: 0L
        return TroubleshootState(
            debugInfo = debugInfo.ifBlank { buildFallbackDebugInfo() },
            hookMethods = hookMethods.ifBlank {
                "尚未收到主进程 Hook 方法列表。\n先打开一次小米钱包卡片页，让 Hook 完成初始化后再回来刷新。"
            },
            updatedAt = updatedAt,
        )
    }

    private fun loadModuleStatus(): ModuleStatusState {
        val service = App.xposedService ?: return loadLegacyHookStatus()
        val frameworkLabel = readFrameworkLabel(service)
        val scope = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        return if (HookEnvironment.TARGET_PACKAGE in scope) {
            ModuleStatusState(
                level = ModuleStatusLevel.ACTIVE,
                title = "模块已激活",
                detail = "$frameworkLabel\n作用域已覆盖 ${HookEnvironment.TARGET_PACKAGE}",
            )
        } else {
            val scopeSummary = if (scope.isEmpty()) "当前框架还没有返回作用域列表。" else "当前作用域: ${scope.joinToString()}"
            ModuleStatusState(
                level = ModuleStatusLevel.INACTIVE,
                title = "模块未激活",
                detail = "$frameworkLabel\n$scopeSummary",
            )
        }
    }

    /**
     * LSPosed 1.x loads the legacy XposedBridge entry point but does not necessarily expose
     * libxposed's service API. Use the hook process's persisted initialization report as the
     * activation signal instead of treating a missing libxposed Binder connection as Hook failure.
     */
    private fun loadLegacyHookStatus(): ModuleStatusState {
        val context = App.appContext
        val prefs = context?.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        val reportedAt = prefs?.getLong(HookEnvironment.PREF_KEY_TROUBLESHOOT_UPDATED_AT, 0L) ?: 0L
        if (reportedAt <= 0L) {
            return ModuleStatusState(
                level = ModuleStatusLevel.WAITING,
                title = "等待 Hook 上报",
                detail = "当前未连接 libxposed 服务，且尚未收到钱包进程的 Hook 初始化上报。打开一次小米钱包卡片页后再回来刷新。",
            )
        }

        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(reportedAt))
        return ModuleStatusState(
            level = ModuleStatusLevel.ACTIVE,
            title = "旧版 Hook 已上报",
            detail = "最近一次钱包进程 Hook 初始化上报：$formattedTime\n当前未连接 libxposed 服务；LSPosed 1.x 下这不代表旧版 Xposed Hook 未运行。",
        )
    }

    private fun loadCardState(): CardSnapshotState {
        val context = App.appContext ?: return CardSnapshotState(warning = "模块进程尚未初始化，暂时无法读取本地快照。")
        val prefs = context.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(HookEnvironment.PREF_KEY_CARD_SNAPSHOTS, "[]").orEmpty()
        val cards = parseCards(raw)
        val error = prefs.getString(HookEnvironment.PREF_KEY_LAST_ERROR, "").orEmpty()
        return CardSnapshotState(
            cards = cards,
            lastUpdated = prefs.getLong(HookEnvironment.PREF_KEY_LAST_UPDATED, 0L),
            lastSource = prefs.getString(HookEnvironment.PREF_KEY_LAST_SOURCE, "").orEmpty(),
            warning = error,
        )
    }

    private fun buildFallbackDebugInfo(): String {
        val service = App.xposedService
        return buildString {
            append("尚未收到主进程调试信息。\n")
            append("先打开一次小米钱包卡片页，让模块在 com.miui.tsmclient 主进程里完成初始化。")
            if (service != null) {
                append("\n\n当前应用侧框架信息\n")
                append(readFrameworkLabel(service))
            }
        }
    }

    private fun readFrameworkLabel(service: XposedService): String = runCatching {
        buildString {
            append(service.frameworkName)
            append(' ')
            append(service.frameworkVersion)
            append(" (build ")
            append(service.frameworkVersionCode)
            append(") · API ")
            append(service.apiVersion)
        }
    }.getOrElse { "libxposed 服务已连接" }

    private fun parseCards(raw: String): List<CardSnapshot> = runCatching {
        val jsonArray = JSONArray(if (raw.isBlank()) "[]" else raw)
        buildList(jsonArray.length()) {
            repeat(jsonArray.length()) { index ->
                val item = jsonArray.optJSONObject(index) ?: JSONObject()
                add(CardSnapshot.fromJson(item))
            }
        }.sortedWith(compareBy<CardSnapshot>({ it.cardName.isBlank() }, { it.title.lowercase() }, { it.cardType }))
    }.getOrElse { emptyList() }
}

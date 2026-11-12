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

object ModuleStateRepository {

    fun loadHomeState(): HomeState {
        val cardState = loadCardState()
        val moduleStatus = loadModuleStatus(cardState.cards.size)
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

    private fun loadModuleStatus(cardCount: Int): ModuleStatusState {
        val service = App.xposedService ?: return loadLegacyHookStatus(cardCount)
        val scope = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        return if (HookEnvironment.TARGET_PACKAGE in scope) {
            ModuleStatusState(
                level = ModuleStatusLevel.ACTIVE,
                title = "模块已激活",
                detail = activeStatusDetail(cardCount),
            )
        } else {
            ModuleStatusState(
                level = ModuleStatusLevel.INACTIVE,
                title = "模块未激活",
                detail = "请在 LSPosed 中启用模块，并确认已勾选小米智能卡。",
            )
        }
    }

    /** LSPosed 1.x may not expose libxposed service; use the target process report instead. */
    private fun loadLegacyHookStatus(cardCount: Int): ModuleStatusState {
        val context = App.appContext
        val prefs = context?.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        val reportedAt = prefs?.getLong(HookEnvironment.PREF_KEY_TROUBLESHOOT_UPDATED_AT, 0L) ?: 0L
        if (reportedAt <= 0L) {
            return ModuleStatusState(
                level = ModuleStatusLevel.WAITING,
                title = "等待模块启动",
                detail = "还没有检测到模块运行。请确认已在 LSPosed 启用模块并勾选小米智能卡，然后重新打开小米智能卡。",
            )
        }
        return ModuleStatusState(
            level = ModuleStatusLevel.ACTIVE,
            title = "模块已激活",
            detail = activeStatusDetail(cardCount),
        )
    }

    private fun activeStatusDetail(cardCount: Int): String = if (cardCount > 0) {
        "已读取到 $cardCount 张卡片，功能正常，无需处理。"
    } else {
        "模块已启动，但还没有读到卡片。打开小米智能卡的卡片列表，再点“同步快照”。"
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

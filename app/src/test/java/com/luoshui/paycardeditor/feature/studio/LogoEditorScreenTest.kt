package com.luoshui.paycardeditor.feature.studio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import com.luoshui.paycardeditor.data.CardAssetRepository
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoEditorScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longPressMenuAddsLogoWithoutRemovingExistingActions() {
        val asset = makeAsset()
        rule.setContent { PayCardTheme { CardStudioScreen(
            uiState = CardStudioUiState(assets = listOf(asset), isLoading = false, actionsSheet = AssetActionsSheet(asset)),
            onEvent = {}, showMessage = {},
        ) } }
        listOf("编辑", "替换", "插入 Logo", "删除").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun pickerSupportsCategoriesSearchAndCustomImport() {
        rule.setContent { PayCardTheme { LogoPickerDialog({}, {}) } }
        rule.waitUntil(15000) { rule.onAllNodesWithText("上海公共交通卡").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("金融").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("从手机导入 Logo").assertIsDisplayed()
        rule.onNodeWithText("搜索名称或类别").performTextInput("上海")
        rule.waitUntil(15000) { rule.onAllNodesWithText("上海公共交通卡").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("上海公共交通卡").assertIsDisplayed()
        screenshot("logo-picker")
    }

    @Test fun editorNamesNewCardAndKeepsOriginal() {
        val asset = makeAsset()
        val original = File(asset.absolutePath).readBytes()
        var savedName: String? = null
        var savedFile: File? = null
        rule.setContent { PayCardTheme { LogoEditorDialog(asset, false, { file, name -> savedFile = file; savedName = name }, {}) } }
        rule.waitUntil(15000) { rule.onAllNodesWithText("添加 Logo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("另存为新卡面").assertIsNotEnabled()
        rule.onNodeWithText("添加 Logo").performScrollTo().performClick()
        rule.onNodeWithText("搜索名称或类别").performTextInput("上海")
        rule.waitUntil(15000) { rule.onAllNodesWithText("上海公共交通卡").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("上海公共交通卡").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("不透明度：100%").fetchSemanticsNodes().isNotEmpty() }
        screenshot("logo-editor")
        rule.onNodeWithText("另存为新卡面").performScrollTo()
        rule.onNodeWithText("另存为新卡面").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("卡面名称").performTextClearance()
        rule.onNodeWithText("保存", useUnmergedTree = false).assertIsNotEnabled()
        rule.onNodeWithText("卡面名称").performTextInput("我的上海交通卡")
        rule.mainClock.advanceTimeBy(100)
        screenshot("logo-name")
        rule.onNodeWithText("保存", useUnmergedTree = false).performClick()
        rule.waitUntil(15000) { savedName != null }
        assertEquals("我的上海交通卡", savedName)
        assertArrayEquals(original, File(asset.absolutePath).readBytes())
        assertTrue(requireNotNull(savedFile).length() > 0)
    }

    @Test fun cancelDoesNotSaveOrChangeOriginal() {
        val asset = makeAsset()
        val original = File(asset.absolutePath).readBytes()
        var saved = false
        var dismissed = false
        rule.setContent { PayCardTheme { LogoEditorDialog(asset, false, { _, _ -> saved = true }, { dismissed = true }) } }
        rule.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertFalse(saved)
        assertArrayEquals(original, File(asset.absolutePath).readBytes())
    }

    private fun makeAsset(): com.luoshui.paycardeditor.data.CardAsset {
        val source = File(RuntimeEnvironment.getApplication().cacheDir, "test-card.png")
        val bitmap = Bitmap.createBitmap(960, 605, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(35, 70, 100)) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return CardAssetRepository.saveAsset(source, "原卡面")
    }

    private fun screenshot(name: String) {
        rule.runOnIdle {
            val view = requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().last { it.isShowing }.window).decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = File("build/test-screenshots/$name.png").apply { requireNotNull(parentFile).mkdirs() }
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

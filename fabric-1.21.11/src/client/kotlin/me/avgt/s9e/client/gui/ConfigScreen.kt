package me.avgt.s9e.client.gui

import me.avgt.s9e.client.Neko
import me.avgt.s9e.common.lang.LanguageNames
import me.avgt.s9e.common.log.NekoLog
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The `/t config` settings page.
 *
 * <p>The layout is deliberately compact (320x244) so it still fits a 320x240 GUI at the
 * lowest common window sizes; everything is positioned from [panelLeft]/[panelTop].
 */
class ConfigScreen : Screen(Component.literal("Neko Translator 设置")) {
    private lateinit var baseUrlBox: EditBox
    private lateinit var apiKeyBox: EditBox
    private lateinit var modelButton: Button
    private lateinit var languageButton: Button
    private lateinit var thinkingButton: Button

    private var panelLeft = 0
    private var panelTop = 0
    private var panelWidth = 320
    private var status: String = ""

    override fun init() {
        panelWidth = minOf(324, width - 8)
        panelLeft = (width - panelWidth) / 2
        panelTop = maxOf(2, (height - PANEL_HEIGHT) / 2)

        val left = panelLeft + 12
        val contentWidth = panelWidth - 24
        val config = Neko.config

        addRenderableWidget(
            Checkbox.builder(Component.literal("启用翻译"), font)
                .pos(left, panelTop + 20)
                .maxWidth(contentWidth)
                .selected(config.enabled)
                .onValueChange { _, value ->
                    config.enabled = value
                    Neko.saveConfig()
                    Neko.chat.rebuildAll()
                }
                .build()
        )

        baseUrlBox = EditBox(font, left, panelTop + 50, contentWidth, 18, Component.literal("API 地址"))
        baseUrlBox.setMaxLength(1000)
        baseUrlBox.setHint(Component.literal("https://api.openai.com/v1"))
        baseUrlBox.value = config.apiBaseUrl
        addRenderableWidget(baseUrlBox)

        apiKeyBox = EditBox(font, left, panelTop + 81, contentWidth, 18, Component.literal("API 密钥"))
        apiKeyBox.setMaxLength(1000)
        apiKeyBox.setHint(Component.literal("sk-..."))
        apiKeyBox.value = config.apiKey
        addRenderableWidget(apiKeyBox)

        modelButton = addRenderableWidget(
            Button.builder(modelLabel(), Button.OnPress { openModelScreen() })
                .bounds(left, panelTop + 112, contentWidth - 66, 18)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("刷新模型"), Button.OnPress { refreshModels() })
                .bounds(left + contentWidth - 62, panelTop + 112, 62, 18)
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.literal("允许他人委托我翻译"), font)
                .pos(left, panelTop + 134)
                .maxWidth(contentWidth)
                .selected(config.allowOthers)
                .onValueChange { _, value -> config.allowOthers = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.literal("委托他人翻译"), font)
                .pos(left, panelTop + 152)
                .maxWidth(contentWidth)
                .selected(config.useOthers)
                .onValueChange { _, value -> config.useOthers = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.literal("自动翻译新消息"), font)
                .pos(left, panelTop + 170)
                .maxWidth(contentWidth)
                .selected(config.autoTranslate)
                .onValueChange { _, value -> config.autoTranslate = value; Neko.saveConfig() }
                .build()
        )

        val languageWidth = contentWidth * 3 / 5 - 2
        val thinkingLeft = left + contentWidth * 3 / 5 + 2
        val thinkingWidth = contentWidth - (contentWidth * 3 / 5) - 2

        languageButton = addRenderableWidget(
            Button.builder(languageLabel(), Button.OnPress {
                config.targetLanguage = LanguageNames.next(config.targetLanguage)
                languageButton.message = languageLabel()
                Neko.saveConfig()
            }).bounds(left, panelTop + 189, languageWidth, 18).build()
        )

        thinkingButton = addRenderableWidget(
            Button.builder(thinkingLabel(), Button.OnPress {
                config.reasoningEffort = nextThinkingLevel(config.reasoningEffort)
                thinkingButton.message = thinkingLabel()
                Neko.saveConfig()
            }).bounds(thinkingLeft, panelTop + 189, thinkingWidth, 18).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("完成"), Button.OnPress { onClose() })
                .bounds(left, panelTop + 220, contentWidth, 18)
                .build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val left = panelLeft
        val top = panelTop
        val right = panelLeft + panelWidth
        val bottom = panelTop + PANEL_HEIGHT

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x70000000)
        graphics.fill(left, top, right, bottom, 0xF0181818.toInt())
        graphics.fill(left, top, right, top + 1, 0xFF3A3A3A.toInt())
        graphics.fill(left, bottom - 1, right, bottom, 0xFF3A3A3A.toInt())

        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, title, left + 12, top + 6, 0xFFFFFFFF.toInt())
        graphics.drawString(font, Component.literal("API 地址"), left + 12, top + 40, 0xFFAAAAAA.toInt())
        graphics.drawString(font, Component.literal("API 密钥"), left + 12, top + 71, 0xFFAAAAAA.toInt())
        graphics.drawString(font, Component.literal("模型"), left + 12, top + 102, 0xFFAAAAAA.toInt())

        val hint = if (status.isNotEmpty()) status else ""
        graphics.drawString(font, Component.literal(hint), left + 12, top + 209, 0xFF888888.toInt())
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        applyEditors()
        Neko.saveConfig(immediate = true)
        minecraft.setScreen(null)
    }

    private fun applyEditors() {
        if (this::baseUrlBox.isInitialized) Neko.config.apiBaseUrl = baseUrlBox.value.trim()
        if (this::apiKeyBox.isInitialized) Neko.config.apiKey = apiKeyBox.value.trim()
    }

    private fun openModelScreen() {
        applyEditors()
        Neko.saveConfig(immediate = true)
        minecraft.setScreen(ModelSelectScreen())
    }

    private fun refreshModels() {
        applyEditors()
        status = "正在获取模型列表…"
        val baseUrl = Neko.config.apiBaseUrl
        val apiKey = Neko.config.apiKey

        Neko.executor.execute {
            val message = try {
                val models = Neko.http.listModels(baseUrl, apiKey)
                Neko.availableModels = models
                if (Neko.config.model.isBlank() && models.isNotEmpty()) Neko.config.model = models.first()
                "已获取 ${models.size} 个模型"
            } catch (t: Throwable) {
                NekoLog.warn("Model list request failed: ${t.message}")
                t.message ?: "获取模型列表失败"
            }

            net.minecraft.client.Minecraft.getInstance().execute {
                status = message
                if (minecraft.screen === this && this::modelButton.isInitialized) {
                    modelButton.message = modelLabel()
                }
            }
        }
    }

    private fun modelLabel(): Component {
        val model = Neko.config.model
        return Component.literal(if (model.isBlank()) "未选择" else model)
    }

    private fun languageLabel(): Component {
        val code = Neko.config.targetLanguage
        val display = LanguageNames.displayName(code)
        val suffix = if (code == "auto") " (${Neko.language()})" else ""
        return Component.literal("翻译语言：$display$suffix")
    }

    private fun thinkingLabel(): Component {
        val label = when (Neko.config.reasoningEffort) {
            "low" -> "低"
            "medium" -> "中"
            "high" -> "高"
            else -> "关"
        }
        return Component.literal("思考：$label")
    }

    private fun nextThinkingLevel(current: String): String = when (current) {
        "low" -> "medium"
        "medium" -> "high"
        "high" -> "off"
        else -> "low"
    }

    companion object {
        private const val PANEL_HEIGHT = 244
    }
}

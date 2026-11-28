package me.avgt.s9e.client.gui

import me.avgt.s9e.client.Neko
import me.avgt.s9e.client.util.toComponent
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.lang.LanguageNames
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationException
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

/**
 * The `/t config` settings page.
 *
 * <p>The layout is deliberately compact (320x262); everything is positioned from
 * [panelLeft]/[panelTop] and only the smallest windows clip the bottom.
 */
class ConfigScreen : Screen(Component.translatable("neko-translator.config.title")) {
    private lateinit var baseUrlBox: EditBox
    private lateinit var apiKeyBox: EditBox
    private lateinit var modelButton: Button
    private lateinit var languageButton: Button
    private lateinit var thinkingButton: Button

    private var panelLeft = 0
    private var panelTop = 0
    private var panelWidth = 320
    private var status: NekoText? = null

    override fun init() {
        panelWidth = minOf(324, width - 8)
        panelLeft = (width - panelWidth) / 2
        panelTop = maxOf(2, (height - PANEL_HEIGHT) / 2)

        val left = panelLeft + 12
        val contentWidth = panelWidth - 24
        val config = Neko.config

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.enabled"), font)
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

        baseUrlBox = EditBox(font, left, panelTop + 50, contentWidth, 18, Component.translatable("neko-translator.config.api_base"))
        baseUrlBox.setMaxLength(1000)
        baseUrlBox.setHint(Component.literal("https://api.openai.com/v1"))
        baseUrlBox.value = config.apiBaseUrl
        addRenderableWidget(baseUrlBox)

        apiKeyBox = EditBox(font, left, panelTop + 81, contentWidth, 18, Component.translatable("neko-translator.config.api_key"))
        apiKeyBox.setMaxLength(1000)
        apiKeyBox.setHint(Component.literal("sk-..."))
        apiKeyBox.value = config.apiKey
        // One star per character keeps the drawn length and the cursor math equal to the real value.
        apiKeyBox.addFormatter { text, _ ->
            if (apiKeyBox.isFocused) null
            else FormattedCharSequence.forward("*".repeat(text.length), Style.EMPTY)
        }
        addRenderableWidget(apiKeyBox)

        modelButton = addRenderableWidget(
            Button.builder(modelLabel(), Button.OnPress { openModelScreen() })
                .bounds(left, panelTop + 112, contentWidth - 66, 18)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.config.refresh_models"), Button.OnPress { refreshModels() })
                .bounds(left + contentWidth - 62, panelTop + 112, 62, 18)
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.allow_others"), font)
                .pos(left, panelTop + 134)
                .maxWidth(contentWidth / 2)
                .selected(config.allowOthers)
                .onValueChange { _, value -> config.allowOthers = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.use_others"), font)
                .pos(left + contentWidth / 2, panelTop + 134)
                .maxWidth(contentWidth / 2)
                .selected(config.useOthers)
                .onValueChange { _, value -> config.useOthers = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.auto_translate"), font)
                .pos(left, panelTop + 152)
                .maxWidth(contentWidth / 2)
                .selected(config.autoTranslate)
                .onValueChange { _, value -> config.autoTranslate = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.translate_displays"), font)
                .pos(left + contentWidth / 2, panelTop + 152)
                .maxWidth(contentWidth / 2)
                .selected(config.translateDisplays)
                .onValueChange { _, value -> config.translateDisplays = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.translate_titles"), font)
                .pos(left, panelTop + 170)
                .maxWidth(contentWidth / 2)
                .selected(config.translateTitles)
                .onValueChange { _, value -> config.translateTitles = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.translate_bossbar"), font)
                .pos(left + contentWidth / 2, panelTop + 170)
                .maxWidth(contentWidth / 2)
                .selected(config.translateBossbar)
                .onValueChange { _, value -> config.translateBossbar = value; Neko.saveConfig() }
                .build()
        )

        addRenderableWidget(
            Checkbox.builder(Component.translatable("neko-translator.config.translate_scoreboard"), font)
                .pos(left, panelTop + 188)
                .maxWidth(contentWidth)
                .selected(config.translateScoreboard)
                .onValueChange { _, value -> config.translateScoreboard = value; Neko.saveConfig() }
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
            }).bounds(left, panelTop + 207, languageWidth, 18).build()
        )

        thinkingButton = addRenderableWidget(
            Button.builder(thinkingLabel(), Button.OnPress {
                config.reasoningEffort = nextThinkingLevel(config.reasoningEffort)
                thinkingButton.message = thinkingLabel()
                Neko.saveConfig()
            }).bounds(thinkingLeft, panelTop + 207, thinkingWidth, 18).build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.config.done"), Button.OnPress { onClose() })
                .bounds(left, panelTop + 238, contentWidth, 18)
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
        graphics.drawString(font, Component.translatable("neko-translator.config.api_base"), left + 12, top + 40, 0xFFAAAAAA.toInt())
        graphics.drawString(font, Component.translatable("neko-translator.config.api_key"), left + 12, top + 71, 0xFFAAAAAA.toInt())
        graphics.drawString(font, Component.translatable("neko-translator.config.model"), left + 12, top + 102, 0xFFAAAAAA.toInt())

        status?.let { graphics.drawString(font, it.toComponent(), left + 12, top + 227, 0xFF888888.toInt()) }
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
        status = NekoText.of("neko-translator.status.fetching")
        val baseUrl = Neko.config.apiBaseUrl
        val apiKey = Neko.config.apiKey

        Neko.executor.execute {
            val message = try {
                val models = Neko.http.listModels(baseUrl, apiKey)
                Neko.availableModels = models
                if (Neko.config.model.isBlank() && models.isNotEmpty()) Neko.config.model = models.first()
                NekoText.of("neko-translator.status.fetched", models.size)
            } catch (t: Throwable) {
                NekoLog.warn("Model list request failed: ${t.message}")
                if (t is TranslationException) t.text else NekoText.of("neko-translator.status.failed")
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
        return if (model.isBlank()) {
            Component.translatable("neko-translator.config.model_none")
        } else {
            Component.literal(model)
        }
    }

    private fun languageLabel(): Component {
        val code = Neko.config.targetLanguage
        return if (code == LanguageNames.AUTO) {
            Component.translatable("neko-translator.config.language.auto", Neko.language())
        } else {
            Component.translatable(
                "neko-translator.config.language",
                Component.literal(LanguageNames.nativeName(code)),
            )
        }
    }

    private fun thinkingLabel(): Component = Component.translatable(
        "neko-translator.config.thinking",
        Component.literal(Neko.config.reasoningEffort),
    )

    private fun nextThinkingLevel(current: String): String {
        val levels = NekoConfig.REASONING_LEVELS
        return levels[(levels.indexOf(current) + 1) % levels.size]
    }

    companion object {
        private const val PANEL_HEIGHT = 262
    }
}

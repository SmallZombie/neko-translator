package me.avgt.s9e.client.gui

import me.avgt.s9e.client.Neko
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Model picker opened from the settings screen (compact 320x238 layout). */
class ModelSelectScreen : Screen(Component.translatable("neko-translator.models.title")) {
    private var page = 0
    private var panelLeft = 0
    private var panelTop = 0
    private var panelWidth = 320
    private var manualBox: EditBox? = null

    override fun init() {
        panelWidth = minOf(324, width - 8)
        panelLeft = (width - panelWidth) / 2
        panelTop = maxOf(2, (height - PANEL_HEIGHT) / 2)

        val left = panelLeft + 12
        val contentWidth = panelWidth - 24
        val models = Neko.availableModels

        val pageCount = maxOf(1, (models.size + PAGE_SIZE - 1) / PAGE_SIZE)
        if (page >= pageCount) page = pageCount - 1
        if (page < 0) page = 0

        val start = page * PAGE_SIZE
        for (row in 0 until PAGE_SIZE) {
            val index = start + row
            if (index >= models.size) break
            val model = models[index]
            addRenderableWidget(
                Button.builder(Component.literal(model), Button.OnPress {
                    Neko.config.model = model
                    Neko.saveConfig(immediate = true)
                    closeToConfig()
                }).bounds(left, panelTop + 24 + row * 19, contentWidth, 18).build()
            )
        }

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.models.prev"), Button.OnPress {
                if (page > 0) page--
                rebuildWidgets()
            }).bounds(left, panelTop + 172, contentWidth / 2 - 3, 18).build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.models.next"), Button.OnPress {
                if ((page + 1) * PAGE_SIZE < models.size) page++
                rebuildWidgets()
            }).bounds(left + contentWidth / 2 + 3, panelTop + 172, contentWidth / 2 - 3, 18).build()
        )

        val box = EditBox(font, left, panelTop + 192, contentWidth - 52, 18, Component.translatable("neko-translator.models.manual"))
        box.setMaxLength(200)
        box.setHint(Component.translatable("neko-translator.models.manual"))
        box.value = Neko.config.model
        manualBox = addRenderableWidget(box)

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.models.use"), Button.OnPress {
                val value = manualBox?.value?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    Neko.config.model = value
                    Neko.saveConfig(immediate = true)
                    closeToConfig()
                }
            }).bounds(left + contentWidth - 48, panelTop + 192, 48, 18).build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("neko-translator.models.back"), Button.OnPress { closeToConfig() })
                .bounds(left, panelTop + 214, contentWidth, 18).build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val left = panelLeft
        val top = panelTop
        val right = panelLeft + panelWidth
        val bottom = panelTop + PANEL_HEIGHT

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x70000000)
        graphics.fill(left, top, right, bottom, 0xF0181818.toInt())

        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, title, left + 12, top + 6, 0xFFFFFFFF.toInt())

        val models = Neko.availableModels
        if (models.isEmpty()) {
            graphics.drawString(
                font,
                Component.translatable("neko-translator.models.empty"),
                left + 12,
                top + 28,
                0xFFAAAAAA.toInt()
            )
        } else {
            val pages = (models.size + PAGE_SIZE - 1) / PAGE_SIZE
            graphics.drawString(
                font,
                Component.translatable("neko-translator.models.page", page + 1, pages, models.size),
                left + 12,
                top + 161,
                0xFF888888.toInt()
            )
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        closeToConfig()
    }

    private fun closeToConfig() {
        minecraft.setScreen(ConfigScreen())
    }

    companion object {
        private const val PAGE_SIZE = 7
        private const val PANEL_HEIGHT = 238
    }
}

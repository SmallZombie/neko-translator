# Neko Translator

[English](README.md) | [简体中文](README.zh-CN.md)

[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=flat&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Release](https://img.shields.io/github/v/release/SmallZombie/neko-translator?style=flat&logo=github&label=Release)](https://github.com/SmallZombie/neko-translator/releases)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1bd96a?style=flat&logo=modrinth&logoColor=white)](https://modrinth.com/mod/neko-translator)

一个基于大语言模型（LLM）的 Minecraft 实时翻译模组。

## ✨ 功能

- **聊天消息**：翻译任何出现在聊天框中的文本
- **物品名称与描述**：翻译物品的名称及其描述文本
- **告示牌**：翻译告示牌上的文字
- **书与笔**：翻译书本中的内容
- **标题与副标题**：翻译屏幕中央显示的标题和副标题
- **动作栏**：翻译屏幕底部显示的动作提示
- **Boss 栏**：翻译 Boss 血条的展示名称
- **文本展示实体**：翻译文本展示实体展示的文本
- **实体名**：翻译你瞄准的实体头顶的名字

### 委托翻译

不想配置自己的模型提供商？试试委托翻译！

你可以将翻译任务委托给服务器内的其他玩家，由对方使用已配置的大语言模型为你完成翻译。这样，你无需自行配置模型提供商，也能享受 AI 翻译功能。

**安装要求：**

- 如果需要使用委托翻译功能，服务器（或单人游戏的主机）也必须安装此模组。
- 如果不需要委托翻译功能，则仅客户端安装即可。

## 依赖

在启动游戏前，请确保你安装了所需依赖：

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## 使用方法

### 配置模型提供商

如果希望使用自己的大语言模型服务：

1. 在游戏中输入 `/t config`，打开配置 GUI。
2. 填写模型提供商的 **URL** 和 **API Key**。
3. 完成配置后即可开始使用。

Enjoy!

### 使用委托翻译

如果希望委托服务器内的其他玩家为你翻译：

1. 确保服务器（或单人游戏的主机）也安装了本模组。
2. 受托人按照上方的步骤配置好模型提供商。
3. 受托人使用 `/t allow <player>`，允许指定玩家委托翻译。
4. 确保受托人在 `/t config` 中打开了翻译总开关和下方的 "允许他人委托我翻译"。
5. 你使用 `/t link <player>`，与该玩家建立委托关系。
6. 确保你在 `/t config` 中启用了 "委托他人翻译"。
7. 完成链接后，你的翻译任务便会交由对方处理。

Enjoy!

## AI 披露

本模组在开发过程中大量使用了 AI 辅助开发，但所有内容均经过人工审核。

## 许可证

本项目采用 **GNU General Public License v3.0（GPL-3.0）** 许可证开源。

详细内容请参阅 [LICENSE](https://github.com/SmallZombie/neko-translator/blob/main/LICENSE) 文件。

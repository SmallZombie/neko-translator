# Neko Translator

[English](README.md) | [简体中文](README.zh-CN.md)

[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=flat&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Release](https://img.shields.io/github/v/release/SmallZombie/neko-translator?style=flat&logo=github&label=Release)](https://github.com/SmallZombie/neko-translator/releases)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1bd96a?style=flat&logo=modrinth&logoColor=white)](https://modrinth.com/mod/neko-translator)

A real-time Minecraft translation mod powered by Large Language Models (LLMs).

## ✨ Features

- **Chat Messages**: Translate any text that appears in the chat window.
- **Item Names & Descriptions**: Translate item names and their descriptions.
- **Signs**: Translate text displayed on signs.
- **Books**: Translate the contents of written books.
- **Titles & Subtitles**: Translate titles and subtitles displayed in the center of the screen.
- **Action Bar**: Translate action bar messages displayed at the bottom of the screen.
- **Boss Bars**: Translate boss bar display names.
- **Text display entity**: Translates the text displayed by the display entity

### Delegated Translation

Don't want to configure your own LLM provider? Try delegated translation!

You can delegate translation tasks to other players on the server, allowing them to use their configured LLM provider to translate content on your behalf. This way, you can enjoy AI-powered translation without having to configure an LLM provider yourself.

**Installation Requirements:**

- To use delegated translation, this mod must also be installed on the server (or the host in singleplayer).
- If you don't need delegated translation, installing the mod on the client alone is sufficient.

## Dependencies

Make sure the following dependencies are installed before launching the game:

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Usage

### Configure an LLM Provider

To use your own LLM service:

1. Enter `/t config` in-game to open the configuration GUI.
2. Enter your provider's **URL** and **API Key**.
3. Once configured, you're ready to go!

Enjoy!

### Use Delegated Translation

To delegate translation tasks to another player on the server:

1. Make sure the mod is also installed on the server (or the host in singleplayer).
2. The player you want to delegate to must configure their LLM provider using the steps above.
3. They must run `/t allow <player>` to allow you to delegate translation tasks to them.
4. Make sure they have enabled the master translation toggle and the **"Allow others to delegate translations to me"** option in `/t config`.
5. Run `/t link <player>` to establish a delegation link with that player.
6. Make sure you have enabled the **"Delegate translations to others"** option in `/t config`.
7. Once linked, your translation tasks will be handled by the delegated player.

Enjoy!

## AI Disclosure

This mod was developed with extensive assistance from AI. However, all content has been manually reviewed.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

For details, please refer to the [LICENSE](https://github.com/SmallZombie/neko-translator/blob/main/LICENSE) file.

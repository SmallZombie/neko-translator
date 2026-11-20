package me.avgt.s9e.common.lang

object LanguageNames {
    /** Minecraft language code -> name used inside the translation prompt. */
    private val PROMPT_NAMES: Map<String, String> = mapOf(
        "zh_cn" to "Simplified Chinese",
        "zh_tw" to "Traditional Chinese",
        "zh_hk" to "Traditional Chinese (Hong Kong)",
        "en_us" to "English",
        "en_gb" to "English",
        "ja_jp" to "Japanese",
        "ko_kr" to "Korean",
        "ru_ru" to "Russian",
        "uk_ua" to "Ukrainian",
        "es_es" to "Spanish",
        "es_mx" to "Spanish (Mexico)",
        "fr_fr" to "French",
        "de_de" to "German",
        "pt_br" to "Portuguese (Brazil)",
        "pt_pt" to "Portuguese",
        "it_it" to "Italian",
        "pl_pl" to "Polish",
        "tr_tr" to "Turkish",
        "nl_nl" to "Dutch",
        "sv_se" to "Swedish",
        "cs_cz" to "Czech",
        "vi_vn" to "Vietnamese",
        "th_th" to "Thai",
        "id_id" to "Indonesian",
        "ar_sa" to "Arabic",
        "hi_in" to "Hindi",
        "fi_fi" to "Finnish",
        "da_dk" to "Danish",
        "nb_no" to "Norwegian",
        "hu_hu" to "Hungarian",
        "ro_ro" to "Romanian",
        "el_gr" to "Greek",
        "he_il" to "Hebrew",
    )

    /** Languages offered by the settings screen, in cycle order. */
    val CHOICES: List<Pair<String, String>> = listOf(
        "auto" to "跟随客户端",
        "zh_cn" to "简体中文",
        "zh_tw" to "繁體中文",
        "en_us" to "English",
        "ja_jp" to "日本語",
        "ko_kr" to "한국어",
        "ru_ru" to "Русский",
        "es_es" to "Español",
        "fr_fr" to "Français",
        "de_de" to "Deutsch",
        "pt_br" to "Português (BR)",
    )

    fun promptName(languageCode: String): String = PROMPT_NAMES[languageCode.lowercase()] ?: languageCode

    fun displayName(languageCode: String): String {
        if (languageCode == "auto") return "跟随客户端"
        CHOICES.firstOrNull { it.first == languageCode }?.let { return it.second }
        return PROMPT_NAMES[languageCode.lowercase()] ?: languageCode
    }

    fun next(current: String): String {
        val index = CHOICES.indexOfFirst { it.first == current }
        return CHOICES[(index + 1).mod(CHOICES.size)].first
    }
}

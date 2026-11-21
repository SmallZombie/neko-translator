package me.avgt.s9e.common.lang

/**
 * Language codes and names used by the settings screen and by the translation prompt.
 *
 * <p>Display names are the native names on purpose: a language selector should be readable for
 * the speakers of every entry. Only the "follow the client" entry is translated, which is why
 * the settings screen renders it from a language key instead of taking it from here.
 */
object LanguageNames {
    /** Value of the "target language" setting that follows the client language. */
    const val AUTO = "auto"

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

    /** Native display names of the languages offered by the settings screen. */
    private val DISPLAY_NAMES: Map<String, String> = mapOf(
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

    /** Language codes offered by the settings screen, in cycle order. */
    val CHOICES: List<String> = listOf(AUTO) + DISPLAY_NAMES.keys

    fun promptName(languageCode: String): String = PROMPT_NAMES[languageCode.lowercase()] ?: languageCode

    fun nativeName(languageCode: String): String {
        val code = languageCode.lowercase()
        return DISPLAY_NAMES[code] ?: PROMPT_NAMES[code] ?: languageCode
    }

    fun next(current: String): String {
        val index = CHOICES.indexOf(current)
        return CHOICES[(index + 1).mod(CHOICES.size)]
    }
}

package com.funny.submaker.feature.asr

enum class AsrLanguage(
    val code: String?,
    val label: String,
    val shortLabel: String,
) {
    Auto(null, "自动检测", "Auto"),
    Zh("zh", "中文（普通话）", "中"),
    Yue("yue", "粤语", "粤"),
    En("en", "英文", "En"),
    Ja("ja", "日语", "日"),
    De("de", "德语", "De"),
    Ko("ko", "韩语", "韩"),
    Ru("ru", "俄语", "Ru"),
    Fr("fr", "法语", "Fr"),
    Pt("pt", "葡萄牙语", "Pt"),
    Ar("ar", "阿拉伯语", "Ar"),
    It("it", "意大利语", "It"),
    Es("es", "西班牙语", "Es"),
    Hi("hi", "印地语", "Hi"),
    Id("id", "印尼语", "Id"),
    Th("th", "泰语", "Th"),
    Tr("tr", "土耳其语", "Tr"),
    Uk("uk", "乌克兰语", "Uk"),
    Vi("vi", "越南语", "Vi"),
    Cs("cs", "捷克语", "Cs"),
    Da("da", "丹麦语", "Da"),
    Fil("fil", "菲律宾语", "Fil"),
    Fi("fi", "芬兰语", "Fi"),
    Is("is", "冰岛语", "Is"),
    Ms("ms", "马来语", "Ms"),
    No("no", "挪威语", "No"),
    Pl("pl", "波兰语", "Pl"),
    Sv("sv", "瑞典语", "Sv");

    val displayName: String
        get() = if (code == null) label else "$label (${code.uppercase()})"
}

data class AsrRecentMedia(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val updatedAtMs: Long = 0L,
)

data class AsrRecentLanguagePair(
    val source: AsrLanguage,
    val target: AsrLanguage,
    val updatedAtMs: Long = 0L,
)

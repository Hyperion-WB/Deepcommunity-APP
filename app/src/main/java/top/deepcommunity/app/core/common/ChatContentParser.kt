package top.deepcommunity.app.core.common

import java.util.regex.Pattern

data class ParsedChatContent(
    val pureText: String,
    val imageUrls: List<String>,
    val isPureImage: Boolean,
    val isDice: Boolean,
    val dicePoints: Int? = null,
    val isCheckin: Boolean
)

object ChatContentParser {

    // 匹配站内 uploads 图片与常见图片链接
    private val IMAGE_REGEX = Pattern.compile(
        """(?:https?://[^\s/]+)?/uploads/[\w/.\-]+\.(?:webp|gif|png|jpe?g)(?:\?\S*)?|https?://[^\s]+\.(?:webp|gif|png|jpe?g)(?:\?\S*)?""",
        Pattern.CASE_INSENSITIVE
    )

    private val DICE_POINTS_REGEX = Pattern.compile(""""points"\s*:\s*(\d+)""")
    private val JSON_TEXT_REGEX = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")

    /**
     * 格式化完整的消息显示，分离文本与图片列表
     */
    fun parse(content: String, msgType: String = "text"): ParsedChatContent {
        val trimmed = content.trim()

        if (msgType == "dice") {
            val matcher = DICE_POINTS_REGEX.matcher(trimmed)
            val points = if (matcher.find()) matcher.group(1)?.toIntOrNull() ?: 1 else 1
            return ParsedChatContent(
                pureText = "掷出了 $points 点",
                imageUrls = emptyList(),
                isPureImage = false,
                isDice = true,
                dicePoints = points,
                isCheckin = false
            )
        }

        if (msgType == "checkin") {
            return ParsedChatContent(
                pureText = trimmed,
                imageUrls = emptyList(),
                isPureImage = false,
                isDice = false,
                dicePoints = null,
                isCheckin = true
            )
        }

        // 成语接龙 / 每日求签 / 投票或群事件机器人的结构化 JSON 消息
        if (msgType in listOf("chain", "fortune", "poll") || (trimmed.startsWith("{") && trimmed.contains("\"text\""))) {
            val textMatcher = JSON_TEXT_REGEX.matcher(trimmed)
            if (textMatcher.find()) {
                val extracted = textMatcher.group(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                if (extracted.isNotBlank()) {
                    return ParsedChatContent(
                        pureText = extracted,
                        imageUrls = emptyList(),
                        isPureImage = false,
                        isDice = false,
                        dicePoints = null,
                        isCheckin = false
                    )
                }
            }
        }

        val matcher = IMAGE_REGEX.matcher(trimmed)
        val urls = mutableListOf<String>()
        val sb = StringBuffer()

        while (matcher.find()) {
            val matchedUrl = matcher.group()
            urls.add(matchedUrl)
            matcher.appendReplacement(sb, "")
        }
        matcher.appendTail(sb)

        val cleanText = sb.toString().replace("\\n", "\n").trim()
        val isPureImage = (msgType == "image" || msgType == "sticker") || (cleanText.isEmpty() && urls.isNotEmpty())

        return ParsedChatContent(
            pureText = cleanText,
            imageUrls = urls,
            isPureImage = isPureImage,
            isDice = false,
            dicePoints = null,
            isCheckin = false
        )
    }

    /**
     * 将引用内容转换为干净的人类可读文本，将图片地址折叠为 [图片]，绝对不显示裸露路径
     */
    fun formatReplySnippet(snippet: String?, rawContent: String? = null): String {
        val target = snippet?.takeIf { it.isNotBlank() } ?: rawContent?.takeIf { it.isNotBlank() } ?: ""
        if (target.isBlank()) return ""

        val trimmed = target.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"text\"")) {
            val m = JSON_TEXT_REGEX.matcher(trimmed)
            if (m.find()) {
                val txt = m.group(1)?.replace("\\\"", "\"")?.replace("\\n", " ") ?: ""
                if (txt.isNotBlank()) return txt.take(30)
            }
        }

        if (trimmed.contains("points") && trimmed.contains("{")) {
            val m = DICE_POINTS_REGEX.matcher(trimmed)
            if (m.find()) {
                return "[骰子: 掷出 ${m.group(1)} 点]"
            }
        }

        val matcher = IMAGE_REGEX.matcher(trimmed)
        if (matcher.find()) {
            val hasText = matcher.replaceAll("").replace("\\n", " ").trim()
            return if (hasText.isBlank()) {
                "[图片]"
            } else {
                "${hasText.take(24)} [图片]"
            }
        }

        return target.replace("\n", " ").take(30)
    }

    /**
     * 将图片相对路径转换为完整 URL
     */
    fun toFullImageUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            Constants.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
        }
    }
}

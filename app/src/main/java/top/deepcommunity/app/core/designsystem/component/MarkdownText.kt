package top.deepcommunity.app.core.designsystem.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import top.deepcommunity.app.core.common.Constants
import top.deepcommunity.app.core.designsystem.theme.AccentLink
import top.deepcommunity.app.core.designsystem.theme.CodeBackground
import top.deepcommunity.app.core.designsystem.theme.DeepTheme

/**
 * 深度极客社区原生高保真 Markdown / 富文本正文渲染引擎
 * 完整支持：各级标题、引用块、多语言代码块（带复制与语言标签）、行内代码、加粗斜体删除线、超链接、
 * Markdown 图片 ![alt](url)、HTML <img src="..." />、裸图片路径自动解析、分割线、有序/无序列表以及全屏点击预览。
 */
@Composable
fun MarkdownArticleContent(
    contentHtml: String?,
    contentMd: String?,
    modifier: Modifier = Modifier
) {
    val rawText = contentMd?.takeIf { it.isNotBlank() } ?: contentHtml ?: ""
    if (rawText.isBlank()) return

    MarkdownContent(markdown = rawText, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val sanitized = remember(markdown) { preprocessMarkdown(markdown) }
    val lines = sanitized.lines()
    var inCodeBlock = false
    var currentCodeLang = ""
    val codeBlockLines = mutableListOf<String>()

    Column(modifier = modifier.fillMaxWidth()) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 1. 多行代码块 ```language
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(
                        code = codeBlockLines.joinToString("\n"),
                        language = currentCodeLang
                    )
                    codeBlockLines.clear()
                    inCodeBlock = false
                    currentCodeLang = ""
                } else {
                    inCodeBlock = true
                    currentCodeLang = line.trim().removePrefix("```").trim()
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                i++
                continue
            }

            val trimmed = line.trim()

            // 2. 水平分割线 --- 或 ***
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = DeepTheme.extended.border
                )
                Spacer(modifier = Modifier.height(10.dp))
                i++
                continue
            }

            // 3. Markdown 独立图片 ![alt](url)
            val mdImgMatch = Regex("^\\s*!\\[(.*?)\\]\\((.*?)\\)\\s*$").find(line)
            if (mdImgMatch != null) {
                val alt = mdImgMatch.groupValues[1]
                val imgUrl = mdImgMatch.groupValues[2]
                MarkdownNetworkImage(
                    url = imgUrl,
                    alt = alt,
                    onImageClick = { previewImageUrl = it }
                )
                i++
                continue
            }

            // 4. HTML 独立图片 <img ... src="url" ... />
            val htmlImgMatch = Regex("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE).find(line)
            if (htmlImgMatch != null) {
                val imgUrl = htmlImgMatch.groupValues[1]
                MarkdownNetworkImage(
                    url = imgUrl,
                    alt = "图片",
                    onImageClick = { previewImageUrl = it }
                )
                i++
                continue
            }

            // 5. 独立成行的用户上传图片 URL (例如 /uploads/... 或 http...png/jpg)
            if (isBareImageUrl(trimmed)) {
                MarkdownNetworkImage(
                    url = trimmed,
                    alt = "配图",
                    onImageClick = { previewImageUrl = it }
                )
                i++
                continue
            }

            // 6. 各级标题与格式块
            when {
                line.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = line.removePrefix("# ").trim(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                line.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = line.removePrefix("## ").trim(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                line.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = line.removePrefix("### ").trim(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                line.startsWith(">") -> {
                    QuoteBlock(text = line.removePrefix(">").trim())
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = trimmed.removePrefix("- ").removePrefix("* ")
                    BulletPoint(text = content)
                }
                Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val num = trimmed.substring(0, dotIdx + 1)
                    val content = trimmed.substring(dotIdx + 1).trim()
                    NumberedPoint(number = num, text = content)
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    // 行内嵌图片处理：提取并优先展示文字，再紧随展示图片
                    val embeddedImg = Regex("!\\[(.*?)\\]\\((.*?)\\)").find(line)
                    if (embeddedImg != null) {
                        val alt = embeddedImg.groupValues[1]
                        val imgUrl = embeddedImg.groupValues[2]
                        val textWithoutImg = line.replace(embeddedImg.value, "").trim()
                        if (textWithoutImg.isNotBlank()) {
                            RichParagraph(text = textWithoutImg)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        MarkdownNetworkImage(
                            url = imgUrl,
                            alt = alt,
                            onImageClick = { previewImageUrl = it }
                        )
                    } else {
                        RichParagraph(text = line)
                    }
                }
            }
            i++
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlock(
                code = codeBlockLines.joinToString("\n"),
                language = currentCodeLang
            )
        }

        // 大图全屏查看弹层
        if (previewImageUrl != null) {
            BasicAlertDialog(onDismissRequest = { previewImageUrl = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { previewImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(previewImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "查看大图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 预处理 HTML 常见实体与换行
 */
private fun preprocessMarkdown(input: String): String {
    return input
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("<strong>", "**")
        .replace("</strong>", "**")
        .replace("<b>", "**")
        .replace("</b>", "**")
        .replace("<em>", "*")
        .replace("</em>", "*")
        .replace("<i>", "*")
        .replace("</i>", "*")
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            RichParagraph(text = text)
        }
    }
}

@Composable
private fun NumberedPoint(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            RichParagraph(text = text)
        }
    }
}

@Composable
private fun RichParagraph(text: String) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        // 正则匹配 **加粗**、*斜体*、~~删除线~~、`行内代码`、[超链接](url)
        val pattern = Regex("(\\*\\*(.*?)\\*\\*)|(\\*([^*]+)\\*)|(~~(.*?)~~)|(`([^`]+)`)|(\\[(.*?)\\]\\((.*?)\\))")
        val matches = pattern.findAll(text)

        for (match in matches) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }

            when {
                // **加粗**
                match.value.startsWith("**") && match.value.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groupValues[2])
                    }
                }
                // ~~删除线~~
                match.value.startsWith("~~") && match.value.endsWith("~~") -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(match.groupValues[6])
                    }
                }
                // `行内代码`
                match.value.startsWith("`") && match.value.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = CodeBackground.copy(alpha = 0.12f),
                            color = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        append(" ${match.groupValues[8]} ")
                    }
                }
                // [超链接](url)
                match.value.startsWith("[") && match.value.contains("](") -> {
                    withStyle(
                        SpanStyle(
                            color = AccentLink,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(match.groupValues[10])
                    }
                }
                // *斜体*
                else -> {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(match.groupValues[4])
                    }
                }
            }
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }

    Text(
        text = annotatedString,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CodeBlock(code: String, language: String = "") {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodeBackground)
            .border(0.6.dp, DeepTheme.extended.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 代码块顶部语言标签与复制代码按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF888888)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制代码",
                        tint = androidx.compose.ui.graphics.Color(0xFFAAAAAA),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "复制",
                        fontSize = 10.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFAAAAAA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = code,
                color = androidx.compose.ui.graphics.Color(0xFFE6E6E6),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun QuoteBlock(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeepTheme.extended.surfaceElevated)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        RichParagraph(text = text)
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun MarkdownNetworkImage(
    url: String,
    alt: String,
    onImageClick: (String) -> Unit
) {
    val fullUrl = remember(url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            Constants.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(fullUrl)
            .crossfade(true)
            .build(),
        contentDescription = alt,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeepTheme.extended.surfaceElevated)
            .clickable { onImageClick(fullUrl) },
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(DeepTheme.extended.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "图片加载失败: $alt",
                    fontSize = 12.sp,
                    color = DeepTheme.extended.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    )
    Spacer(modifier = Modifier.height(6.dp))
}

private fun isBareImageUrl(str: String): Boolean {
    if (str.startsWith("/uploads/") && (str.endsWith(".png") || str.endsWith(".jpg") || str.endsWith(".jpeg") || str.endsWith(".webp") || str.endsWith(".gif"))) {
        return true
    }
    if ((str.startsWith("http://") || str.startsWith("https://")) &&
        (str.endsWith(".png") || str.endsWith(".jpg") || str.endsWith(".jpeg") || str.endsWith(".webp") || str.endsWith(".gif"))
    ) {
        return true
    }
    return false
}

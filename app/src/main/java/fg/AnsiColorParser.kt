package fg

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

object AnsiColorParser {

    // Menangkap baik karakter ESC asli (\u001B) maupun teks literal "\u001b" dari logcat
    private val ANSI_PATTERN = Regex("(?:\\u001B|\\\\u001[bB])\\[([0-9;]*)m")

    fun stripAnsi(input: String): String {
        return input.replace(ANSI_PATTERN, "")
    }

    fun parse(input: String): CharSequence {
        val builder = SpannableStringBuilder()
        var currentColor: Int? = null
        var isBold = false
        var isDim = false
        var lastIndex = 0

        for (match in ANSI_PATTERN.findAll(input)) {
            if (match.range.first > lastIndex) {
                appendStyled(builder, input.substring(lastIndex, match.range.first), currentColor, isBold, isDim)
            }

            val codes = match.groupValues[1]
                .split(";")
                .mapNotNull { it.toIntOrNull() }
                .ifEmpty { listOf(0) }

            var i = 0
            while (i < codes.size) {
                when (val code = codes[i]) {
                    0 -> {
                        currentColor = null
                        isBold = false
                        isDim = false
                    }
                    1 -> isBold = true
                    2 -> isDim = true
                    22 -> {
                        isBold = false
                        isDim = false
                    }
                    39 -> currentColor = null
                    in 30..37 -> currentColor = standardColor(code - 30, bright = false)
                    in 90..97 -> currentColor = standardColor(code - 90, bright = true)
                    38 -> {
                        // Format 256-color: 38;5;N
                        if (i + 2 < codes.size && codes[i + 1] == 5) {
                            currentColor = xterm256Color(codes[i + 2])
                            i += 2
                        }
                    }
                }
                i++
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < input.length) {
            appendStyled(builder, input.substring(lastIndex), currentColor, isBold, isDim)
        }

        return builder
    }

    private fun appendStyled(
        builder: SpannableStringBuilder,
        text: String,
        color: Int?,
        isBold: Boolean,
        isDim: Boolean
    ) {
        if (text.isEmpty()) return
        val start = builder.length
        builder.append(text)
        val end = builder.length

        val effectiveColor = when {
            color != null && isDim -> adjustAlpha(color, 0.6f)
            color != null -> color
            isDim -> Color.parseColor("#888888")
            else -> null
        }

        if (effectiveColor != null) {
            builder.setSpan(ForegroundColorSpan(effectiveColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isBold) {
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun standardColor(index: Int, bright: Boolean): Int {
        return if (!bright) {
            when (index) {
                0 -> Color.parseColor("#4E4E4E") // Black
                1 -> Color.parseColor("#E06C75") // Red (string literal)
                2 -> Color.parseColor("#98C379") // Green (parameter name)
                3 -> Color.parseColor("#E5C07B") // Yellow (method exit)
                4 -> Color.parseColor("#61AFEF") // Blue
                5 -> Color.parseColor("#C678DD") // Magenta (class name)
                6 -> Color.parseColor("#56B6C2") // Cyan
                else -> Color.parseColor("#DCDFE4") // White
            }
        } else {
            when (index) {
                0 -> Color.parseColor("#7F848E")
                1 -> Color.parseColor("#FF7A85")
                2 -> Color.parseColor("#B5E890")
                3 -> Color.parseColor("#FFD68A")
                4 -> Color.parseColor("#7CC4FF") // Bright Blue (il2cpp)
                5 -> Color.parseColor("#DF95F5")
                6 -> Color.parseColor("#70DCE8")
                else -> Color.WHITE
            }
        }
    }

    private fun xterm256Color(n: Int): Int {
        return when {
            n in 0..7 -> standardColor(n, bright = false)
            n in 8..15 -> standardColor(n - 8, bright = true)
            n in 16..231 -> {
                val idx = n - 16
                val r = (idx / 36) * 51
                val g = ((idx % 36) / 6) * 51
                val b = (idx % 6) * 51
                Color.rgb(r, g, b)
            }
            n in 232..255 -> {
                val gray = 8 + (n - 232) * 10
                Color.rgb(gray, gray, gray)
            }
            else -> Color.WHITE
        }
    }
}

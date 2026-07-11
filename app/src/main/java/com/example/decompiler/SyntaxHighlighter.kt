package com.example.decompiler

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

object SyntaxHighlighter {

    private val javaKeywords = setOf(
        "package", "import", "public", "private", "protected", "class", "interface", 
        "enum", "extends", "implements", "static", "final", "void", "int", "boolean", 
        "byte", "char", "short", "long", "float", "double", "return", "throw", "new", 
        "try", "catch", "finally", "synchronized", "volatile", "transient", "abstract", 
        "native", "strictfp", "synthetic", "false", "true", "null", "super", "this"
    )

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", 
        "init", "constructor", "return", "throw", "try", "catch", "finally", "null", 
        "true", "false", "this", "super", "private", "public", "protected", "internal", 
        "override", "open", "abstract", "final", "companion", "data", "sealed", "enum"
    )

    fun highlight(code: String, isKotlin: Boolean): AnnotatedString {
        val keywords = if (isKotlin) kotlinKeywords else javaKeywords
        
        return buildAnnotatedString {
            append(code)
            
            // 1. Highlight comments (single-line // and multi-line /* */)
            var index = 0
            while (index < code.length) {
                if (index + 1 < code.length && code[index] == '/' && code[index + 1] == '/') {
                    val endOfLine = code.indexOf('\n', index)
                    val end = if (endOfLine == -1) code.length else endOfLine
                    addStyle(
                        SpanStyle(color = Color(0xFF5C6370)), // Muted slate-gray
                        index,
                        end
                    )
                    index = end
                } else if (index + 1 < code.length && code[index] == '/' && code[index + 1] == '*') {
                    val endOfComment = code.indexOf("*/", index + 2)
                    val end = if (endOfComment == -1) code.length else endOfComment + 2
                    addStyle(
                        SpanStyle(color = Color(0xFF5C6370)), // Muted slate-gray
                        index,
                        end
                    )
                    index = end
                } else if (code[index] == '"') {
                    // Highlight string literals
                    var endOfString = code.indexOf('"', index + 1)
                    while (endOfString != -1 && code[endOfString - 1] == '\\') {
                        endOfString = code.indexOf('"', endOfString + 1)
                    }
                    val end = if (endOfString == -1) code.length else endOfString + 1
                    addStyle(
                        SpanStyle(color = Color(0xFF98C379)), // Warm code-green
                        index,
                        end
                    )
                    index = end
                } else {
                    index++
                }
            }

            // 2. Highlight keywords and annotations
            val wordRegex = Regex("[a-zA-Z0-9_@]+")
            wordRegex.findAll(code).forEach { match ->
                val word = match.value
                val start = match.range.first
                val end = match.range.last + 1
                
                // Avoid overriding comments or string styles
                // (Checking if character is in styled block can be simplified:
                // only highlight words that don't overlap with comment colors,
                // but for lightweight native display, a fast check is enough).
                
                if (word.startsWith("@")) {
                    addStyle(
                        SpanStyle(color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold), // Lavender annotation
                        start,
                        end
                    )
                } else if (keywords.contains(word)) {
                    addStyle(
                        SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.Bold), // Vibrant violet keyword
                        start,
                        end
                    )
                } else if (word.matches(Regex("[0-9]+"))) {
                    addStyle(
                        SpanStyle(color = Color(0xFFD19A66)), // Peach/Orange numbers
                        start,
                        end
                    )
                } else if (word[0].isUpperCase() && !word.contains("_")) {
                    // Possible class type
                    addStyle(
                        SpanStyle(color = Color(0xFFE5C07B)), // Gold class type
                        start,
                        end
                    )
                }
            }
        }
    }
}

package io.alexjoest.stackupup.rules.io

internal object MarkdownGateParser {
    fun parse(source: String): MarkdownGateParseResult {
        val tokenizer = Tokenizer(source)
        val tokens = tokenizer.tokenize()
        if (tokens.error != null) {
            return MarkdownGateParseResult.Failure(tokens.error.message, tokens.error.offset)
        }
        return Parser(tokens.tokens).parse()
    }

    private class Parser(private val tokens: List<Token>) {
        private var index = 0

        fun parse(): MarkdownGateParseResult {
            if (peek().type == TokenType.END) {
                return failure("Empty gate expression", peek().offset)
            }
            val expression = parseOr() ?: return lastFailure
            if (peek().type != TokenType.END) {
                return failure("Unexpected token '${peek().text}'", peek().offset)
            }
            return MarkdownGateParseResult.Success(expression)
        }

        private fun parseOr(): RuleGateExpression? {
            var expression = parseAnd() ?: return null
            while (match(TokenType.OR)) {
                val right = parseAnd() ?: return null
                expression = RuleGateExpression.Or(expression, right)
            }
            return expression
        }

        private fun parseAnd(): RuleGateExpression? {
            var expression = parseUnary() ?: return null
            while (match(TokenType.AND)) {
                val right = parseUnary() ?: return null
                expression = RuleGateExpression.And(expression, right)
            }
            return expression
        }

        private fun parseUnary(): RuleGateExpression? {
            if (match(TokenType.NOT)) {
                return RuleGateExpression.Not(parseUnary() ?: return null)
            }
            return parseFunctionCall()
        }

        private fun parseFunctionCall(): RuleGateExpression? {
            val name = consume(TokenType.IDENTIFIER, "Expected gate function") ?: return null
            consume(TokenType.LEFT_PAREN, "Expected '(' after gate function") ?: return null
            val args = parseArgList() ?: return null
            consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments") ?: return null
            return buildExpression(name.text, args, name.offset)
        }

        private fun parseArgList(): List<String>? {
            val args = ArrayList<String>()
            if (peek().type == TokenType.RIGHT_PAREN) {
                return args
            }
            val first = consume(TokenType.STRING, "Expected quoted string argument") ?: return null
            args += first.text
            while (match(TokenType.COMMA)) {
                val next = consume(TokenType.STRING, "Expected quoted string argument after ','") ?: return null
                args += next.text
            }
            return args
        }

        private fun buildExpression(name: String, args: List<String>, offset: Int): RuleGateExpression? = when (name) {
            "state" -> {
                if (args.size != 1) {
                    fail("state() takes exactly 1 argument", offset)
                    null
                } else {
                    RuleGateExpression.State(args[0])
                }
            }
            "modLoaded" -> RuleGateExpression.ModLoaded(args.map { it.lowercase() })
            else -> {
                fail("Unknown gate function '$name'", offset)
                null
            }
        }

        private fun match(type: TokenType): Boolean {
            if (peek().type != type) return false
            index++
            return true
        }

        private fun consume(type: TokenType, message: String): Token? {
            val token = peek()
            if (token.type == type) {
                index++
                return token
            }
            fail(message, token.offset)
            return null
        }

        private fun peek(): Token = tokens[index]

        private var lastFailure: MarkdownGateParseResult.Failure = MarkdownGateParseResult.Failure("", 0)

        private fun fail(message: String, offset: Int) {
            lastFailure = MarkdownGateParseResult.Failure(message, offset)
        }

        private fun failure(message: String, offset: Int): MarkdownGateParseResult.Failure = MarkdownGateParseResult.Failure(message, offset)
    }

    private class Tokenizer(private val source: String) {
        fun tokenize(): TokenizeResult {
            val tokens = ArrayList<Token>()
            var index = 0
            while (index < source.length) {
                when (val char = source[index]) {
                    ' ', '\t', '\r', '\n' -> index++
                    '!' -> {
                        tokens += Token(TokenType.NOT, "!", index)
                        index++
                    }
                    '&' -> {
                        if (source.getOrNull(index + 1) != '&') return error("Expected '&' for &&", index)
                        tokens += Token(TokenType.AND, "&&", index)
                        index += 2
                    }
                    '|' -> {
                        if (source.getOrNull(index + 1) != '|') return error("Expected '|' for ||", index)
                        tokens += Token(TokenType.OR, "||", index)
                        index += 2
                    }
                    '(' -> {
                        tokens += Token(TokenType.LEFT_PAREN, "(", index)
                        index++
                    }
                    ')' -> {
                        tokens += Token(TokenType.RIGHT_PAREN, ")", index)
                        index++
                    }
                    ',' -> {
                        tokens += Token(TokenType.COMMA, ",", index)
                        index++
                    }
                    '"' -> {
                        val string = readString(index)
                        if (string.error != null) return error(string.error.message, string.error.offset)
                        tokens += Token(TokenType.STRING, string.value, index)
                        index = string.nextIndex
                    }
                    else -> {
                        if (!isIdentifierStart(char)) return error("Unexpected character '$char'", index)
                        val start = index
                        index++
                        while (index < source.length && isIdentifierPart(source[index])) index++
                        tokens += Token(TokenType.IDENTIFIER, source.substring(start, index), start)
                    }
                }
            }
            tokens += Token(TokenType.END, "", source.length)
            return TokenizeResult(tokens, null)
        }

        private fun readString(start: Int): StringReadResult {
            val builder = StringBuilder()
            var index = start + 1
            while (index < source.length) {
                val char = source[index]
                if (char == '"') return StringReadResult(builder.toString(), index + 1, null)
                if (char == '\\') {
                    val next = source.getOrNull(index + 1)
                        ?: return StringReadResult("", index, TokenizeError("Unterminated escape", index))
                    builder.append(next)
                    index += 2
                    continue
                }
                builder.append(char)
                index++
            }
            return StringReadResult("", start, TokenizeError("Unterminated string", start))
        }

        private fun isIdentifierStart(char: Char): Boolean = char == '_' || char.isLetter()

        private fun isIdentifierPart(char: Char): Boolean = char == '_' || char.isLetterOrDigit()

        private fun error(message: String, offset: Int): TokenizeResult = TokenizeResult(emptyList(), TokenizeError(message, offset))
    }

    private enum class TokenType {
        IDENTIFIER,
        STRING,
        NOT,
        AND,
        OR,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        END,
    }

    private data class Token(val type: TokenType, val text: String, val offset: Int)
    private data class TokenizeResult(val tokens: List<Token>, val error: TokenizeError?)
    private data class TokenizeError(val message: String, val offset: Int)
    private data class StringReadResult(val value: String, val nextIndex: Int, val error: TokenizeError?)
}

package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.craftinginterpreters.lox.Token;
import com.craftinginterpreters.lox.TokenType;

import static com.craftinginterpreters.lox.TokenType.*;

public class LoxScanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    LoxScanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while(!isAtEnd()) {
            start = current; // begin of a new lexeme
            scanToken(); // get current token
        }

        // add EOF token at last
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;

            // 需要往后读一个字符(one-character lookahead)判断是
            // "!"or"!=" "="or"==" "<"or"<=" ">"or">=" "/"or"//"
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_THAN : LESS); break;
            case '>': addToken(match('=') ? GREATER_THAN : GREATER); break;
            case '/':
                if (match('/')) {
                    // 是注释，直接跳过一行
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // ignore whitespace
            case ' ': case '\t': case '\r': break;

            case '\n': line++; break;

            // 注意以上都还只用了addToken(type) 因为没有literal，这个是开始用addToken(type, literal)
            // string literal
            case '"': getStringLiteral(); break;

            default:
                // number literal
                if (isDigit(c)) {
                    getNumberLiteral();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void getNumberLiteral() {
        // 不支持小数点在头尾的情况

        // 整数部分
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // consume the '.'

            // 小数部分
            while (isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void getStringLiteral() {
        // 先找到结束的"，注意可能遇到\n EOF
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\n') line++; // 轻松支持多行string - 作者说其实仅允许单行string更复杂
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        advance(); // skip '"'

        String string_literal = source.substring(start+1, current-1);
        addToken(STRING, string_literal);
        // 注意这里lexeme和literal区别仅在于是否带""
    }

    // match和peek
    // peek和match都会查看下一个字符，match可能推进一个字符
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() {
        // 查看当前char是啥
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        // 查看下一个char是啥
        if (current+1 >= source.length()) return '\0';
        return source.charAt(current+1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        // 这种是知道TokenType就能完全知道token所有信息的情况，如+/(/...
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        // token: type lexeme(str) literal line
        // lexeme按照start-current截取str, line由Scanner维护, 遇'\n'自增
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}

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

            default:
                Lox.error(line, "Unexpected character.");
                break;
        }
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

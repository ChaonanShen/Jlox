package com.craftinginterpreters.lox;

public class Token {
    final TokenType type; // final修饰变量表示常量，初次赋值后便不能再改
    final String lexeme;
    final Object literal;
    final int line;

    Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}

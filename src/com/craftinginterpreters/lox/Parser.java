package com.craftinginterpreters.lox;

import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            // 现在只有expression没有statement，所以遇到错误直接返回到最上层
            // 之后有了statement再进行error recovery(synchronization等)
            return null;
        }
    }

    /*
    expression -> equality ;
    equality   -> comparison ( ( "!=" | "==" ) comparison )* ;
    comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    term       -> factor ( ( "-" | "+" ) factor )*;
    factor     -> unary ( ( "/" | "*" ) unary )* ;
    unary      -> ( "!" | "-" ) unary
                | primary ;
    primary    -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;
     */

    private Expr expression() {
        return equality();
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous(); // 因为match会advance跳过那个operator
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_THAN, LESS, LESS_THAN)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(PLUS, MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        } else {
            return primary();
        }
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Except ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // 好像没有处理tokens只有一个EOF的情景？

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) { // 可传入多个type类型
        for (TokenType type : types) {
            if (check(type)) { // check会检查isAtEnd
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // 只要捕获ParseError 就调用synchronize()恢复状态
    // 不过目前还只能解析expression，之后能解析statement再使用，目前直接抛出错误即可
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}

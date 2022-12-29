package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // 对外公共接口
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    // ---- parse statements ----
    /*
    program     -> declaration* EOF ;
    declaration -> varDecl
                 | funDecl
                 | statement ;

    varDecl     -> "var" IDENTIFIER ( "=" expression )? ";" ;

    funDecl     -> "fun" function ;
    function    -> IDENTIFIER "(" parameters? ")" block ;
    parameters  -> IDENTIFIER ( "," IDENTIFIER )* ;

    statement   -> exprStmt
                 | forStmt
                 | ifStmt
                 | printStmt
                 | returnStmt
                 | whileStmt
                 | block ;
    exprStmt    -> expression ";" ;
    forStmt     -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
    ifStmt      -> "if" "(" expression ")" statement ( "else" statement )? ;
    printStmt   -> "print" expression ";" ;
    returnStmt  -> "return" expression? ";" ;
    whileStmt   -> "while" "(" expression ")" statement;
    block       -> "{" declaration* "}" ;
    */

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            if (match(FUN)) return funDeclaration();
            return statement();
        } catch (ParseError error) { // 注意只有进入panic mode后才会throw error，那时候parser is confused，需要synchronize()将parser调整到能继续parse的状态
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }
    private Stmt funDeclaration() {
        return function("function");
    }
    private Stmt.Function function(String kind) { // 区分normal functions & class methods
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return functionLeft(name, kind);
    }

    private Stmt.Function functionLeft(Token name, String kind) {
        consume(LEFT_PAREN, "Expect '(' before parameters.");
        List<Token> params = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            // 有形参
            do {
                if (params.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }
                params.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, params, body);
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        else if (match(LEFT_BRACE)) return new Stmt.Block(block());
        else if (match(IF)) return ifStatement();
        else if (match(WHILE)) return whileStatement();
        else if (match(FOR)) return forStatement();
        else if (match(RETURN)) return returnStatement();
        else return expressionStatement(); // 没法从第一个token判断是否是expressionStatement
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if-condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after while.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while-condition.");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        Stmt initStmt;
        Expr conditionExpr;
        Expr updateExpr;
        Stmt body;

        consume(LEFT_PAREN, "Expect '(' after for.");

        // init statement
        if (match(SEMICOLON)) {
            initStmt = null;
        } else if (match(VAR)) {
            initStmt = varDeclaration();
        } else {
            initStmt = expressionStatement();
        }
        // test expression
        if (match(SEMICOLON)) {
            conditionExpr = null;
        } else {
            conditionExpr = expression();
            consume(SEMICOLON, "Expect ';' after test expression.");
        }
        // update expression -- 可能没有
        if (match(RIGHT_PAREN)) {
            updateExpr = null;
        } else {
            updateExpr = expression();
            consume(RIGHT_PAREN, "Expect ')' after for condition.");
        }

        body = statement();

        // 直接转换成while循环 - 总归使用Block组合语句块
        if (conditionExpr == null)
            conditionExpr = new Expr.Literal(true);

        if (updateExpr != null) { // merge body & updateExpr
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(updateExpr)));
        }
        body = new Stmt.While(conditionExpr, body);

        if (initStmt != null) { // merge initStmt & body (注意initStmt只执行一次)
            body = new Stmt.Block(Arrays.asList(initStmt, body));
        }

        return body;
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr expr = null;
        if (!check(SEMICOLON)) {
            expr = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, expr);
    }

    private Stmt printStatement() {
        Expr value = expression(); // 匹配一个expression
        consume(SEMICOLON, "Expect ';' after value."); // must be ; at last
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() { // 之后的function body等也都复用block()
        // 注意block前已经匹配掉左花括号{
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }


    // ---- parse expressions ----
    /*
    expression -> assignment ;
    assignment -> IDENTIFIER "=" assignment
                | logic_or ;
    logic_or   -> logic_and ( "or" logic_and )* ;
    logic_and  -> equality ( "and" equality )* ;
    equality   -> comparison ( ( "!=" | "==" ) comparison )* ;
    comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    term       -> factor ( ( "-" | "+" ) factor )*;
    factor     -> unary ( ( "/" | "*" ) unary )* ;
    unary      -> ( "!" | "-" ) unary | call ;
    call       -> primary ( "(" arguments? ")" )* ;
    arguments  -> ( expression | lambda ) ( "," ( expression | lambda ) )* ;
    lambda     -> "fun" "(" parameters? ")" block ; // 借用Stmt中function的匹配，只不过没有了函数名
    primary    -> NUMBER | STRING
                | "true" | "false" | "nil"
                | "(" expression ")" | IDENTIFIER;
     */

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = logic_or();

        if (match(EQUAL)) { // 说明是assignment
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr logic_or() {
        Expr expr = logic_and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = logic_and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr logic_and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
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

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
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
            return call();
        }
    }

    private Expr call() {
        Expr callee = primary();
        while (match(LEFT_PAREN)) { // 进入第一个函数调用
            List<Expr> args = new ArrayList<>();
            if (!check(RIGHT_PAREN)) { // 有参数
                do {
                    if (args.size() >= 255) { // instance method最多容纳254 args，因为还有个this参数
                        error(peek(), "Can't have more than 255 arguments.");
                    }
                    if (match(FUN)) {
                        args.add(new Expr.Lambda(functionLeft(null, "lambda")));
                    } else {
                        args.add(expression());
                    }
                } while(match(COMMA));
            }
            Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
            callee = new Expr.Call(callee, paren, args); // callee可能已经是个函数调用Expr.Call
        }

        return callee;
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

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
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

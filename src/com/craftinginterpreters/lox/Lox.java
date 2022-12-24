package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static boolean hadError = false; // 确保不执行有错的代码 (Lox应该是那种只会有一个实例对象的类)

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    // execute from the source code file
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
    }

    // execute from the cmdline
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false; // 即使某一行出错，报错即可，不直接退出
        }
    }

    // 用run每次解析一行source string
    private static void run(String source) {
        // Scanner/Token自定义
        LoxScanner scanner = new LoxScanner(source);
        List<Token> tokens = scanner.scanTokens();

        // for now, just print all tokens
        for (Token token : tokens) {
            System.out.println(token);
        }
    }

    // report error
    static void error(int line, String msg) {
        report(line, "", msg);
    }
    private static void report(int line, String where, String msg) {
        System.err.println("[line " + line + "] Error" + where + ": " + msg);
        hadError = true;
    }
}

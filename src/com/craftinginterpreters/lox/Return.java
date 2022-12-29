package com.craftinginterpreters.lox;

public class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null, null, false, false); // 关闭一些JVM machinery，毕竟不是真的抛异常
        this.value = value;
    }
}

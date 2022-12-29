package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private Environment environment = globals; // environment会随着scope切换而改变，而globals一直指向最top level的那个

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }
            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }
            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    // 对外公共接口
    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private void execute(Stmt stmt) { // 调用这个就能按照Stmt类型调用各自接口
        stmt.accept(this); // 比如Print语句就会调用this(interpreter).visitPrintStmt(stmt)
    }

    private Object evaluate(Expr expr) { // 调用这个就能按照Expr类型调用各自接口
        return expr.accept(this); // 比如Binary就会调用this(interpreter).visitBinaryExpr(expr)
    }

    // 注意不光是普通block执行，函数body执行也用executeBlock，只不过传入的environment是函数自身作用域
    // 让interpreter在一个新environment下执行，执行结束恢复原先环境
    public void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment; // 直接替换当前Interpreter的environment

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous; // 恢复环境
        }
    }

    // Stmt.Visitor<Void>的几个override函数
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) { // 这样就允许variable不必一定要初始化
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        boolean cond = isTruthy(evaluate(stmt.condition));
        if (cond) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) { // 这个实现有点像直接Lox映射到Java
        while (isTruthy(evaluate(stmt.condition))) { // 执行body后environment会不同，重新eval(cond)会不同
            execute(stmt.body);
        }
        return null;
    }

    // 下面四个是Expr.Visitor<Object>的四个override函数
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        // 注意 operand的evaluate顺序是left-to-right，这在operands有side effects的情况很重要

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            // 注意上面几个比较只能比较double之间，但!=和==可以比较不同类型之间
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);

            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String && right instanceof String) { // concatenate strings
                    return left + (String)right;
                }
                // throw error?
                break;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) { // call type check
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() +
                            " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) { // 特别注意 短路法则 right在不必要eval时不应该eval，因为可能有side effect！
        Object left = evaluate(expr.left);

        switch (expr.operator.type) { // 自己写的时候没法写出这样简洁的代码！
            case OR:
                if (isTruthy(left)) return left;
                else return evaluate(expr.right);
            case AND:
                if (!isTruthy(left)) return left;
                else return evaluate(expr.right);
            default:
                throw new RuntimeError(expr.operator, "Logical operator must be OR/AND.");
        }
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right); // 调用right subexpression的visit

        // 是否要判断下right的类型是否正确？
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }

        // unreachable
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) { // 遇到一个variable，直接返回值
        return environment.get(expr.name);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (boolean)obj;
        return true; // 其他不管Double(哪怕是0)还是String都是true
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b); // 有趣的是只要a不是null，这个a.equals(b)的结果就是Lox想要的
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length()-2);
            }
            return text;
        }

        return object.toString();
    }
}

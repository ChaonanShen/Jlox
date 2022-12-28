package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0]; // IDEA中设置下program arguments(设置成lox文件夹路径)

        // 定义Expr类那一套visit pattern
        defineAst(outputDir, "Expr", Arrays.asList(
            "Assign   : Token name, Expr value",
            "Binary   : Expr left, Token operator, Expr right",
            "Grouping : Expr expression",
            "Literal  : Object value",
            "Logical  : Expr left, Token operator, Expr right",
            "Unary    : Token operator, Expr right",
            "Variable : Token name"
        ));

        // 定义Stmt类那一套visit pattern
        defineAst(outputDir, "Stmt", Arrays.asList(
            "Block      : List<Stmt> statements",
            "Expression : Expr expression",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            "Print      : Expr expression",
            "Var        : Token name, Expr initializer",
            "While      : Expr condition, Stmt body",
            "Break      :  "
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        PrintWriter writer = new PrintWriter(outputDir + "/" + baseName + ".java","UTF-8");

        writer.println("package com.craftinginterpreters.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");

        // the base accept() method -- 作者把这个放在abstract class Expr的末尾，我觉得放在开头清晰些
        writer.println("  abstract <R> R accept(Visitor<R> visitor);");
        writer.println();

        // generate visitor interface 里面包含各个expression classes的visitXXX方法
        defineVisitor(writer, baseName, types); // the visitor pattern

        // generate AST classes.
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim(); // maybe ""
            defineType(writer, baseName, className, fields);
        }

        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("  interface Visitor<R> {");
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }
        writer.println("  }");
    }

    private static void defineType(PrintWriter writer,
                                   String baseName, String className, String fieldList) { // fieldList maybe ""
        // e.g. baseName-"Expr" className-"Binary" fieldList-"Expr left, Token operator, Expr right"

        writer.println();
        // define class
        writer.println("  static class " + className + " extends " + baseName + " {");

        // constructor
        writer.println("    " + className + "(" + fieldList + ") {");
        // store parameters in fields
        if (fieldList.trim().length() > 0) {
            String[] fields = fieldList.split(", ");
            for (String field : fields) {
                String name = field.split(" ")[1];
                writer.println("      this." + name + " = " + name + ";");
            }
        }
        writer.println("    }");

        // visitor pattern
        writer.println();
        writer.println("    @Override");
        writer.println("    <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        // fields definition
        if (fieldList.trim().length() > 0) {
            String[] fields = fieldList.split(", ");
            for (String field : fields) {
                writer.println("    final " + field + ";");
            }
        }

        writer.println("  }");
    }
}

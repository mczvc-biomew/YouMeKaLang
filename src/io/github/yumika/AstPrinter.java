package io.github.yumika;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
  String print(Expr expr) { return expr.accept(this); }

  String print(Stmt stmt) { return stmt.accept(this); }

  @Override
  public String visitBlockStmt(Stmt.Block stmt) {
    StringBuilder builder = new StringBuilder();
    builder.append("(block ");

    for (Stmt statement : stmt.statements) {
      builder.append(statement.accept(this));
    }

    builder.append(")");
    return builder.toString();
  }

  public String visitCaseStmt(Stmt.Case stmt) {
    StringBuilder builder = new StringBuilder();
    builder.append("(case " + stmt.expression);

    for (Stmt.Case.WhenClause clause : stmt.whenClauses) {
      builder.append("\n{when: " + clause.match + " [" + clause.body + "]}");
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  public String visitClassStmt(Stmt.Class stmt) {
    StringBuilder builder = new StringBuilder();
    builder.append("(class " + stmt.name.lexeme);

    if (stmt.superclass != null) {
      builder.append(" < " + print(stmt.superclass));
    }

    for (Map.Entry<String, Stmt.Function> method : stmt.methods.entrySet()) {
      builder.append(" " + print(method.getValue()));
    }

    builder.append(")");
    return builder.toString();
  }

  @Override
  public String visitDestructuringVarStmt(Stmt.DestructuringVarStmt stmt) {
    // @TODO: loop through fields
    return parenthesize2("destructuring var" + stmt.fields, stmt.initializer);
  }

  @Override
  public String visitExpressionStmt(Stmt.Expression stmt) { return parenthesize(";", stmt.expression) ; }

  @Override
  public String visitForStmt(Stmt.For stmt) {
    return parenthesize2("for ", stmt.initializer, stmt.condition, stmt.increment, stmt.body);
  }

  @Override
  public String visitForEachStmt(Stmt.ForEach stmt) {
    return parenthesize2("for-each", stmt.variable, stmt.iterable, stmt.body);
  }

  @Override
  public String visitFunctionStmt(Stmt.Function stmt) {
    StringBuilder builder = new StringBuilder();
    builder.append("(fun " + stmt.name.lexeme + "(");

    for (Token param : stmt.params) {
      if (param != stmt.params.get(0)) builder.append(" ");
      builder.append(param.lexeme);
    }

    builder.append(") ");

    for (Stmt body : stmt.body) {
      builder.append(body.accept(this));
    }

    builder.append(")");
    return builder.toString();
  }

  @Override
  public String visitIfStmt(Stmt.If stmt) {
    if (stmt.elseBranch == null) {
      return parenthesize2("if", stmt.condition, stmt.thenBranch);
    }

    return parenthesize2("if-else", stmt.condition, stmt.thenBranch,
        stmt.elseBranch);
  }

  @Override
  public String visitImportStmt(Stmt.Import stmt) {
    return parenthesize2("import", stmt.pathParts, stmt.alias);
  }

  @Override
  public String visitInterfaceStmt(Stmt.Interface stmt) {
    return parenthesize2("interface", stmt.name, stmt.methods);
  }

  @Override
  public String visitPrintStmt(Stmt.Print stmt) { return parenthesize("print", stmt.expression); }

  @Override
  public String visitPutsStmt(Stmt.Puts stmt) { return parenthesize("puts", stmt.expression ); }

  @Override
  public String visitReturnStmt(Stmt.Return stmt) {
    if (stmt.value == null) return "(return)";
    return parenthesize("return", stmt.value);
  }

  @Override
  public String visitThrowStmt(Stmt.Throw stmt) {
    return parenthesize2("throw", stmt.error);
  }

  @Override
  public String visitTryCatchStmt(Stmt.TryCatch stmt) {
    return parenthesize2("try (catch)", stmt.errorVar);
  }

  @Override
  public String visitTypeDefStmt(Stmt.TypeDef stmt) {
    return parenthesize2("type definition", stmt.name, stmt.definition);
  }

  @Override
  public String visitVarStmt(Stmt.Var stmt) {
    if (stmt.initializer == null) {
      return parenthesize2("var", stmt.name);
    }

    return parenthesize2("var", stmt.name, "=", stmt.initializer);
  }

  @Override
  public String visitWhileStmt(Stmt.While stmt) { return parenthesize2("while", stmt.condition, stmt.body); }

  @Override
  public String visitListLiteralExpr(Expr.ListLiteral expr) { return parenthesize2("[]", expr.elements); }

  @Override
  public String visitArrayAssignExpr(Expr.ArrayAssign expr) {
    return "";
  }

  @Override
  public String visitArrayIndexExpr(Expr.ArrayIndex expr) { return parenthesize2("[]", expr.index, expr.array); }

  @Override
  public String visitAssignExpr(Expr.Assign expr) { return parenthesize2("=", expr.name.lexeme, expr.value); }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme,
        expr.left, expr.right);
  }

  @Override
  public String visitBlockExpr(Expr.Block expr) {
    return parenthesize2("||", expr.statements);
  }

  @Override
  public String visitCallExpr(Expr.Call expr) { return parenthesize2("call", expr.callee, expr.positionalArgs, expr.keywordArgs); }

  @Override
  public String visitCaseExpr(Expr.Case expr) {

    StringBuilder builder = new StringBuilder();
    builder.append("(case expression" + expr.expression);

    for (Expr.Case.WhenClause clause : expr.whenClauses) {
      builder.append("\n{when: " + clause.match + " [" + clause.result + "]}");
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  public String visitCompoundAssignExpr(Expr.CompoundAssign expr) {
    return parenthesize2(expr.name.lexeme, expr.operator.lexeme, expr.value);
  }

  @Override
  public String visitFunctionExpr(Expr.Function expr) {
    return parenthesize2("function expr", expr.params, expr.hasVarArgs, expr.kwArgsName, expr.varArgsName, expr.kwArgsName);
  }

  @Override
  public String visitGetExpr(Expr.Get expr) { return parenthesize2(".", expr.object, expr.name.lexeme); }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) { return parenthesize("group", expr.expression); }

  @Override
  public String visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
    return parenthesize2("template-string", expr.parts);
  }
  @Override
  public String visitLambdaExpr(Expr.Lambda expr) {
    return parenthesize2("lambda", expr.body, expr.params);
  }

  @Override
  public String visitListComprehensionExpr(Expr.ListComprehension expr) {
    return parenthesize2("[list-comprehension]", expr.elementExpr, expr.iterable, expr.condition);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "null";
    return (expr.value instanceof String ? "\"" : "") + expr.value.toString() + (expr.value instanceof String ? "\"" : "");
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitMatchExpr(Expr.Match expr) {
    return parenthesize2("match", expr.value, expr.cases);
  }

  @Override
  public String visitNewTypedArrayExpr(Expr.NewTypedArray expr) {
    return parenthesize2("new", expr.type, expr.size);
  }

  @Override
  public String visitNullCoalesceExpr(Expr.NullCoalesce expr) {
    return parenthesize2("??", expr.left, expr.operator, expr.right);
  }

  @Override
  public String visitOptionalGetExpr(Expr.OptionalGet expr) {
    return parenthesize2("?.", expr.object, expr.name);
  }

  @Override
  public String visitPipelineExpr(Expr.Pipeline expr) {
    return parenthesize2("pipeline", expr.left, ", fun:", expr.right);
  }

  @Override
  public String visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
    return parenthesize2("{:}", expr.properties);
  }

  @Override
  public String visitPostfixExpr(Expr.Postfix expr) {
    return parenthesize2("postfix", expr.operator, expr.variable);
  }

  @Override
  public String visitPrefixExpr(Expr.Prefix expr) {
    return parenthesize2("prefix", expr.operator, expr.variable);
  }

  @Override
  public String visitSetExpr(Expr.Set expr) {
    return parenthesize2("=",
        expr.object, expr.name.lexeme, expr.value);
  }

  @Override
  public String visitSpreadExpr(Expr.Spread expr) {
    return parenthesize("spread", expr.expression);
  }

  @Override
  public String visitSuperExpr(Expr.Super expr) { return parenthesize2("super", expr.method); }

  @Override
  public String visitThisExpr(Expr.This expr) { return "this"; }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) { return parenthesize(expr.operator.lexeme, expr.right); }

  @Override
  public String visitUndefinedExpr(Expr.Undefined expr) { return "undefined"; }

  @Override
  public String visitVariableExpr(Expr.Variable expr) { return expr.name.lexeme; }

  @Override
  public String visitYieldExpr(Expr.Yield expr) {
    return parenthesize2("yield", expr.keyword, expr.value);
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  private String parenthesize2(String name, Object... parts) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    transform(builder, parts);
    builder.append(")");

    return builder.toString();
  }

  private void transform(StringBuilder builder, Object... parts) {
    for (Object part : parts) {
      builder.append(" ");
      if (part instanceof Expr) {
        builder.append(((Expr)part).accept(this));
      } else if (part instanceof Stmt) {
        builder.append(((Stmt) part).accept(this));
      } else if (part instanceof Token) {
        builder.append(((Token)part).lexeme);
      } else if (part instanceof List) {
        transform(builder, ((List)part).toArray());
      } else {
        builder.append(part);
      }
    }
  }

  public static void main(String[] args) {
    Expr expression = new Expr.Binary(
        new Expr.Unary(
            new Token(TokenType.MINUS, "-", null, 1),
            new Expr.Literal(123)),
        new Token(TokenType.STAR, "*", null, 1),
        new Expr.Grouping(
            new Expr.Literal(45.67)));

    System.out.println(new AstPrinter().print(expression));

    List<Expr> elements = Arrays.asList(new Expr.Literal("1"));
    Expr listComp = new Expr.ListComprehension(
        new Expr.Variable(
            new Token(TokenType.VAR, "x", 'x', 1)
        ),
        new Token(TokenType.VAR, "x", 'x', 1),
        new Expr.ListLiteral(elements),
        new Expr.Binary(
            new Expr.Literal("1"),
            new Token(TokenType.LESS, "<", "<", 1),
            new Expr.Literal("2")
        )
    );
    System.out.println(new AstPrinter().print(listComp));
    try {
      runFile("helloWorld.ymk");
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  private static void runFile(String fileName) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(fileName));
    run(new String(bytes, Charset.defaultCharset()));
  }

  private static void run(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

    Parser parser = new Parser(tokens);
    List<Stmt> statements = parser.parse();
    AstPrinter printer = new AstPrinter();

    for (Stmt statement : statements) {
      System.out.println(printer.print(statement));
    }
  }
}

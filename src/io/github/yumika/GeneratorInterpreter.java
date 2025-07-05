package io.github.yumika;

import java.util.List;

public class GeneratorInterpreter {
  private final Stmt.Function declaration;
  private final Environment closure;
  private Environment environment;
  private int currentStmtIndex = 0;
  private boolean finished = false;

  private final List<Stmt> body;

  public static class GeneratorComplete extends RuntimeException {}

  public static class YieldException extends RuntimeException {
    final Object value;

    YieldException(Object value) {
      super(null, null, false, false);
      this.value = value;
    }
  }

  public GeneratorInterpreter(Stmt.Function declaration, Environment closure) {
    this.declaration = declaration;
    this.closure = closure;
    this.environment = new Environment(closure);
    this.body = declaration.body;
  }

  public Object resume() {
    if (finished) throw new GeneratorComplete();

    try {
      while (currentStmtIndex < body.size()) {
        Stmt stmt = body.get(currentStmtIndex++);
        execute(stmt);
      }
      finished = true;
      throw new GeneratorComplete();
    } catch (YieldException yield) {
      return yield.value;
    }
  }

  private void execute(Stmt stmt) {
    stmt.accept(new GeneratorExecutor());
  }

  private class GeneratorExecutor implements Stmt.Visitor<Void>, Expr.Visitor<Object> {
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
      executeBlock(stmt.statements, new Environment(environment));
      return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
      Object value = null;
      if (stmt.initializer != null) {
        value = evaluate(stmt.initializer);
      }
      environment.define(stmt.name.lexeme, value);
      return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
      evaluate(stmt.expression);
      return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
      return null;
    }

    @Override
    public Void visitForEachStmt(Stmt.ForEach stmt) {
      return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
      Object condition = evaluate(stmt.condition);
      if (isTruthy(condition)) {
        execute(stmt.thenBranch);
      } else if (stmt.elseBranch != null) {
        execute(stmt.elseBranch);
      }
      return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
      finished = true;
      throw new GeneratorComplete();
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
      while (isTruthy(evaluate(stmt.condition))) {
        execute(stmt.body);
      }
      return null;
    }

    // === Expr Visitors (minimal)
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
      // Add operator support here
      return null; // implement if needed
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
      return expr.value;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
      return environment.get(expr.name);
    }

    @Override
    public Void visitYieldExpr(Expr.Yield expr) {
      Object value = evaluate(expr.value);
      throw new YieldException(value);
    }

    private void executeBlock(List<Stmt> statements, Environment newEnv) {
      Environment previous = environment;
      try {
        environment = newEnv;
        for (Stmt stmt : statements) {
          execute(stmt);
        }
      } finally {
        environment = previous;
      }
    }

    private Object evaluate(Expr expr) {
      return expr.accept(this);
    }

    private boolean isTruthy(Object obj) {
      if (obj == null) return false;
      if (obj instanceof Boolean) return (Boolean) obj;
      return true;
    }

    // @TODO: Start here
    // Add other statement/expr visitors as needed
    @Override
    public Void visitImportStmt(Stmt.Import stmt) {
      return null;
    }

    @Override
    public Void visitCaseStmt(Stmt.Case stmt) {
      return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
      return null;
    }

    @Override
    public Void visitDestructuringVarStmt(Stmt.DestructuringVarStmt stmt) {
      return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
      return null;
    }

    @Override
    public Void visitInterfaceStmt(Stmt.Interface stmt) {
      return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
      return null;
    }

    @Override
    public Void visitPutsStmt(Stmt.Puts stmt) {
      return null;
    }


    @Override
    public Void visitThrowStmt(Stmt.Throw stmt) {
      return null;
    }

    @Override
    public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
      return null;
    }

    @Override
    public Void visitTypeDefStmt(Stmt.TypeDef stmt) {
      return null;
    }

    // @TODO: Expression starts here
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
      return null;
    }

    @Override
    public Object visitMatchExpr(Expr.Match expr) {
      return null;
    }

    @Override
    public Object visitNewTypedArrayExpr(Expr.NewTypedArray expr) {
      return null;
    }

    @Override
    public Object visitNullCoalesceExpr(Expr.NullCoalesce expr) {
      return null;
    }

    @Override
    public Object visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
      return null;
    }

    @Override
    public Object visitOptionalGetExpr(Expr.OptionalGet expr) {
      return null;
    }

    @Override
    public Object visitPipelineExpr(Expr.Pipeline expr) {
      return null;
    }

    @Override
    public Object visitPostfixExpr(Expr.Postfix expr) {
      return null;
    }

    @Override
    public Object visitPrefixExpr(Expr.Prefix expr) {
      return null;
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
      return null;
    }

    @Override
    public Object visitSpreadExpr(Expr.Spread expr) {
      return null;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
      return null;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
      return null;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
      return null;
    }

    @Override
    public Object visitUndefinedExpr(Expr.Undefined expr) {
      return null;
    }

    @Override
    public Object visitArrayAssignExpr(Expr.ArrayAssign expr) {
      return null;
    }

    @Override
    public Object visitArrayIndexExpr(Expr.ArrayIndex expr) {
      return null;
    }

    @Override
    public Object visitBlockExpr(Expr.Block expr) {
      return null;
    }

    @Override
    public Object visitCaseExpr(Expr.Case expr) {
      return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
      return null;
    }

    @Override
    public Object visitCompoundAssignExpr(Expr.CompoundAssign expr) {
      return null;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
      return null;
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
      return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
      return null;
    }

    @Override
    public Object visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
      return null;
    }

    @Override
    public Object visitLambdaExpr(Expr.Lambda expr) {
      return null;
    }

    @Override
    public Object visitListComprehensionExpr(Expr.ListComprehension expr) {
      return null;
    }

    @Override
    public Object visitListLiteralExpr(Expr.ListLiteral expr) {
      return null;
    }

  }
}

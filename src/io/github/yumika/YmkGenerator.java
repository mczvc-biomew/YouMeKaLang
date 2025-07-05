package io.github.yumika;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YmkGenerator implements YmkCallable {
  static class GeneratorDetectedException extends RuntimeException {}

  private final Environment closure;
  private final Stmt.Function declaration;
  private final GeneratorInterpreter interpreter;

  public YmkGenerator(Stmt.Function declaration, Environment closure) {
    this.declaration = declaration;
    this.closure = closure;

    this.interpreter = new GeneratorInterpreter(declaration, closure);
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments, Map<String, Object> kwargs) {
    Environment generatorEnv = new Environment(closure);
    for (int i = 0; i < declaration.params.size(); i++) {
      Token param = declaration.params.get(i);
      Object value = i < arguments.size() ? arguments.get(i) : null;
      generatorEnv.define(param.lexeme, value);
    }
    GeneratorFrame frame = new GeneratorFrame(declaration, generatorEnv);
    return new BoundGenerator(frame);
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public String toString() { return "<generator>"; }

  public static class BoundGenerator {
    private final GeneratorFrame frame;
    private boolean isDone = false;

    public BoundGenerator(GeneratorFrame frame) {
      this.frame = frame;
    }

    public Object next() {
      if (isDone) return makeResult(null, true);
      try {
        Object val = frame.resume();
        return makeResult(val, false);
      } catch (GeneratorInterpreter.GeneratorComplete e) {
        isDone = true;
        return makeResult(YmkUndefined.INSTANCE, true);
      }
    }

    private Map<String, Object> makeResult(Object value, boolean done) {
      Map<String, Object> result = new HashMap<>();
      result.put("value", value);
      result.put("done", done);
      return result;
    }

    @Override
    public String toString() {
      return "<generator instance>";
    }
  }

  static class GeneratorDetector implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    @Override
    public Void visitYieldExpr(Expr.Yield expr) {
      throw new GeneratorDetectedException();
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
      return stmt.expression.accept(this);
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
    public Void visitVarStmt(Stmt.Var stmt) {
      if (stmt.initializer != null) {
        stmt.initializer.accept(this);
      }
      return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
      // Don't scan inner function bodies
      return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
      stmt.condition.accept(this);
      stmt.thenBranch.accept(this);
      if (stmt.elseBranch != null) stmt.elseBranch.accept(this);
      return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
      stmt.condition.accept(this);
      stmt.body.accept(this);
      return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
      if (stmt.value != null) stmt.value.accept(this);
      return null;
    }

    // TODO: Start here;

    @Override
    public Void visitImportStmt(Stmt.Import stmt) {
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

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
      for (Stmt s : stmt.statements) s.accept(this);
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
    public Void visitArrayAssignExpr(Expr.ArrayAssign expr) {
      return null;
    }

    @Override
    public Void visitArrayIndexExpr(Expr.ArrayIndex expr) {
      return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
      return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
      return null;
    }

    @Override
    public Void visitBlockExpr(Expr.Block expr) {
      return null;
    }

    @Override
    public Void visitCaseExpr(Expr.Case expr) {
      return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
      return null;
    }

    @Override
    public Void visitCompoundAssignExpr(Expr.CompoundAssign expr) {
      return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
      return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
      return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
      return null;
    }

    @Override
    public Void visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
      return null;
    }

    @Override
    public Void visitLambdaExpr(Expr.Lambda expr) {
      return null;
    }

    @Override
    public Void visitListComprehensionExpr(Expr.ListComprehension expr) {
      return null;
    }

    @Override
    public Void visitListLiteralExpr(Expr.ListLiteral expr) {
      return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
      return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
      return null;
    }

    @Override
    public Void visitMatchExpr(Expr.Match expr) {
      return null;
    }

    @Override
    public Void visitNewTypedArrayExpr(Expr.NewTypedArray expr) {
      return null;
    }

    @Override
    public Void visitNullCoalesceExpr(Expr.NullCoalesce expr) {
      return null;
    }

    @Override
    public Void visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
      return null;
    }

    @Override
    public Void visitOptionalGetExpr(Expr.OptionalGet expr) {
      return null;
    }

    @Override
    public Void visitPostfixExpr(Expr.Postfix expr) {
      return null;
    }

    @Override
    public Void visitPrefixExpr(Expr.Prefix expr) {
      return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
      return null;
    }

    @Override
    public Void visitSpreadExpr(Expr.Spread expr) {
      return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
      return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
      return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
      return null;
    }

    @Override
    public Void visitUndefinedExpr(Expr.Undefined expr) {
      return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
      return null;
    }
  }
}

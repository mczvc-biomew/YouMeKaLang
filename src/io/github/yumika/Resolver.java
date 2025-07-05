package io.github.yumika;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();

  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) { this.interpreter = interpreter; }

  private enum FunctionType {
    NONE,
    // function-type-method
    FUNCTION,
    // function-type-initializer
    INITIALIZER,
    METHOD,
    LAMBDA,
    GENERATOR
  }

  private enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  private ClassType currentClass = ClassType.NONE;

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitCaseStmt(Stmt.Case stmt) {
    resolve(stmt.expression);
    for (Stmt.Case.WhenClause clause : stmt.whenClauses) {
      resolve(clause.match);
      resolve(clause.body);
    }
    if (stmt.elseBranch != null) {
      resolve(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    // set-current-class
    declare(stmt.name);
    define(stmt.name);

    // inherit-self
    if (stmt.superclass != null &&
        stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      YouMeKa.error(stmt.superclass.name,
          "A class can't inherit from itself.");
    }

    if (stmt.superclass != null) {
      // set-current-subclass
      currentClass = ClassType.SUBCLASS;
      resolve(stmt.superclass);
    }

    // Inheritance begin-super-scope
    if (stmt.superclass != null) {
      beginScope();
      scopes.peek().put("super", true);
    }

    // resolve-methods
    // resolver-begin-this-scope
    beginScope();
    scopes.peek().put("this", true);

    for (Map.Entry<String, Stmt.Function> method : stmt.methods.entrySet()) {
      FunctionType declaration = FunctionType.METHOD;

      if (method.getKey().equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }

      resolveFunction(method.getValue(), declaration);
    }

    // resolver-end-this-scope
    endScope();

    // Inheritance end-super-scope
    if (stmt.superclass != null) endScope();

    // restore-current-class
    currentClass = enclosingClass;

    return null;
  }

  @Override
  public Void visitDestructuringVarStmt(Stmt.DestructuringVarStmt stmt){
    // First resolve the initializer expression
    resolve(stmt.initializer);

    // Declare each field in destructured patter
    for (Stmt.DestructuringVarStmt.DestructuringField field : stmt.fields) {
      declare(field.name);
      if (field.defaultValue != null) {
        resolve(field.defaultValue);
      }
      define(field.name);
    }

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitForEachStmt(Stmt.ForEach stmt) {
    beginScope(); // Create a new local scope for the loop variable

    // Declare and define the loop variable
    declare(stmt.variable);
    define(stmt.variable);

    // Resolve the iterable expression
    resolve(stmt.iterable);

    // Resolve the loop body
    resolve(stmt.body);

    endScope(); // End the for-loop scope
    return null;
  }

  @Override
  public Void visitForStmt(Stmt.For stmt) {
    beginScope();

    // Resolve initializer (e.g., var i = 0)
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    // Resolve condition (e.g., i < 10)
    if (stmt.condition != null) {
      resolve(stmt.condition);
    }

    // Resolve increment (e.g., i += 1)
    if (stmt.increment != null) {
      resolve(stmt.increment);
    }

    // Resolve loop body
    resolve(stmt.body);

    endScope();
    return null;
  }


  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    FunctionType declaration = interpreter.isGeneratorFunction(stmt) ? FunctionType.GENERATOR : FunctionType.FUNCTION;
    declare(stmt.name);
    define(stmt.name);

    // pass-function-type
    resolveFunction(stmt, declaration);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitImportStmt(Stmt.Import stmt) {
    if (stmt.path.lexeme.startsWith("java.")) {
      return null;
    }
    declare(stmt.alias);
    define(stmt.alias);

    return null;
  }

  @Override
  public Void visitInterfaceStmt(Stmt.Interface stmt) {
    declare(stmt.name);
    define(stmt.name);

    for (Stmt.Function method : stmt.methods) {
      declare(method.name);
      define(method.name);
    }

    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitPutsStmt(Stmt.Puts stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
//      YouMeKa.error(stmt.keyword, "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      // Classes return-in-initializer
      if (currentFunction == FunctionType.INITIALIZER) {
        YouMeKa.error(stmt.keyword, "Can't return a value from an initializer.");
      }

      resolve(stmt.value);
    }
    return null;
  }

  @Override
  public Void visitThrowStmt(Stmt.Throw stmt) {
    resolve(stmt.error);

    return null;
  }

  @Override
  public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
    resolve(stmt.tryBlock);

    // Catch block get a new scope
    beginScope();
    declare(stmt.errorVar);
    define(stmt.errorVar);

    resolve(stmt.catchBlock);
    endScope();

    return null;
  }

  @Override
  public Void visitTypeDefStmt(Stmt.TypeDef stmt) {
    declare(stmt.name);
    define(stmt.name);
    resolve(stmt.definition);
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitArrayAssignExpr(Expr.ArrayAssign expr) {
    resolve(expr.array);
    resolve(expr.index);
    resolve(expr.value);
    return null;
  }

  @Override
  public Void visitListLiteralExpr(Expr.ListLiteral expr) {
    if (expr.elements == null) return null;
    for (Expr element : expr.elements) {
      resolve(element);
    }
    return null;
  }

  @Override
  public Void visitSpreadExpr(Expr.Spread expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitArrayIndexExpr(Expr.ArrayIndex expr) {
    resolve(expr.array);
    resolve(expr.index);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name);
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitBlockExpr(Expr.Block expr) {
    resolve(expr.statements);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.positionalArgs) {
      resolve(argument);
    }

    for (Expr kwarg : expr.keywordArgs.values()) {
      resolve(kwarg);
    }

    return null;
  }

  @Override
  public Void visitCaseExpr(Expr.Case expr) {
    resolve(expr.expression);
    for (Expr.Case.WhenClause clause : expr.whenClauses) {
      resolve(clause.match);
      resolve(clause.result);
    }
    if (expr.elseBranch != null) {
      resolve(expr.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitCompoundAssignExpr(Expr.CompoundAssign expr) {
    resolveLocal(expr, expr.name);
    resolve(expr.value);

    return null;
  }

  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    resolveFunction(expr, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
    for (Expr part : expr.parts) {
      resolve(part);
    }
    return null;
  }

  @Override
  public Void visitLambdaExpr(Expr.Lambda expr) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = FunctionType.LAMBDA;

    beginScope();
    for (Token param : expr.params) {
      declare(param);
      define(param);
    }
    resolve(expr.body);
    endScope();

    currentFunction = enclosingFunction;
    return null;
  }

  @Override
  public Void visitListComprehensionExpr(Expr.ListComprehension expr) {
    resolve(expr.iterable);
    if (expr.condition != null)
      resolve(expr.condition);
    resolve(expr.elementExpr);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) { return null; }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitMatchExpr(Expr.Match expr) {
    resolve(expr.value);
    for (Expr.MatchCase kase : expr.cases) {
      if (!kase.isElse && kase.pattern != null) resolve(kase.pattern);
      resolve(kase.body);
    }
    return null;
  }

  @Override
  public Void visitNewTypedArrayExpr(Expr.NewTypedArray expr) {
    resolve(expr.size);
    return null;
  }

  @Override
  public Void visitNullCoalesceExpr(Expr.NullCoalesce expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitOptionalGetExpr(Expr.OptionalGet expr) {
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
    List<Expr.ObjectLiteral.Property> props = ((Expr.ObjectLiteral)expr).properties;
    if (props == null) return null;
    for (Expr.ObjectLiteral.Property prop : props) {
      if (prop instanceof Expr.ObjectLiteral.Pair pair) {
        resolve(pair.value);
      } else if (prop instanceof Expr.ObjectLiteral.Spread spread) {
        resolve(spread.expression);
      }
    }

    return null;
  }

  @Override
  public Void visitPostfixExpr(Expr.Postfix expr) {
    resolve(expr.variable);
    return null;
  }

  @Override
  public Void visitPrefixExpr(Expr.Prefix expr) {
    resolve(expr.variable);
    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    // invalid-super
    if (currentClass == ClassType.NONE) {
      YouMeKa.error(expr.keyword,
          "Can't use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      YouMeKa.error(expr.keyword,
          "Can't use 'super' in a class with no superclass.");
    }

    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    // this-outside-of-class
//    if (currentClass == ClassType.NONE) {
//      YouMeKa.error(expr.keyword, "Can't use 'this' outside of a class.");
//      return null;
//    }

    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  public Void visitUndefinedExpr(Expr.Undefined expr) {
    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty() &&
    scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
      YouMeKa.error(expr.name,
          "Can't read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name);
    return null;
  }

  @Override
  public Void visitYieldExpr(Expr.Yield expr) {
    if (currentFunction != FunctionType.GENERATOR) {
      YouMeKa.error(expr.keyword, "Can't yield outside a generator function.");
    }
    resolve(expr.value);
    return null;
  }

  private void resolve(Stmt stmt) { stmt.accept(this); }
  private void resolve(Expr expr) { expr.accept(this); }
  private void resolveFunction(
      Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
//    for (Token param : function.params) {
    for (int i = 0; i < function.params.size(); i++) {
      Token param = function.params.get(i);
      declare(param);
      define(param);

      if (function.paramTypes.get(i) != null) {
        Token typeToken = function.paramTypes.get(i);
        String expected = typeToken.lexeme;
      }

    }

    if (function.returnType != null) {
      Token returnType = function.returnType;
    }
    resolve(function.body);
    endScope();
    // restore-current-function
    currentFunction = enclosingFunction;
  }

  private void resolveFunction(Expr.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();

    currentFunction = enclosingFunction;
  }

  private void beginScope() { scopes.push(new HashMap<String, Boolean>()); }
  private void endScope() { scopes.pop(); }
  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Boolean> scope = scopes.peek();
    // duplicate-variable
    if (scope.containsKey(name.lexeme)) {
      YouMeKa.error(name,
          "Already declared in this scope.");
    }

    scope.put(name.lexeme, false);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    scopes.peek().put(name.lexeme, true);
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - i);
      }
    }
  }

}

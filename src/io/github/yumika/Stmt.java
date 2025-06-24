package io.github.yumika;

import java.util.List;

abstract class Stmt {
  interface Visitor<R> {
    R visitBlockStmt(Block stmt);
    R visitCaseStmt(Case stmt);
    R visitClassStmt(Class stmt);
    R visitExpressionStmt(Expression stmt);
    R visitFunctionStmt(Function stmt);
    R visitIfStmt(If stmt);
    R visitImportStmt(Import stmt);
    R visitPrintStmt(Print stmt);
    R visitPutsStmt(Puts stmt);
    R visitReturnStmt(Return stmt);
    R visitThrowStmt(Throw stmt);
    R visitTryCatchStmt(TryCatch stmt);
    R visitVarStmt(Var stmt);
    R visitWhileStmt(While stmt);
  }

  static class Block extends Stmt {
    Block(List<Stmt> statements) { this.statements = statements; }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitBlockStmt(this); }

    final List<Stmt> statements;
  }

  static class Case extends Stmt {
    Case(Expr expression, List<WhenClause> whenClauses, Stmt elseBranch) {
      this.expression = expression;
      this.whenClauses = whenClauses;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitCaseStmt(this); }

    final Expr expression;
    final List<WhenClause> whenClauses;
    final Stmt elseBranch;

    static class WhenClause {
      WhenClause(Expr match, Stmt body) {
        this.match = match;
        this.body = body;
      }

      final Expr match;
      final Stmt body;
    }
  }

  static class Class extends Stmt {
    Class(Token name,
          Expr.Variable superclass,
          List<Stmt.Function> methods) {
      this.name = name;
      this.superclass = superclass;
      this.methods = methods;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitClassStmt(this); }

    final Token name;
    final Expr.Variable superclass;
    final List<Stmt.Function> methods;
  }

  static class Expression extends Stmt {
    Expression(Expr expression) { this.expression = expression;}

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitExpressionStmt(this); }

    final Expr expression;
  }

  static class Function extends Stmt {
    Function(Token name, List<Token> params, List<Stmt> body) {
      this.name = name;
      this.params = params;
      this.body = body;
      this.hasVarArgs = false;
      this.hasVarKwargs = false;
      this.varArgsName = null;
      this.kwArgsName = null;
    }

    Function(Token name, List<Token> params, List<Stmt> body,
             boolean hasVarArgs, boolean hasVarKwargs,
             Token varArgsName, Token kwArgsName) {
      this.name = name;
      this.params = params;
      this.body = body;
      this.hasVarArgs = hasVarArgs;
      this.hasVarKwargs = hasVarKwargs;
      this.varArgsName = varArgsName;
      this.kwArgsName = kwArgsName;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitFunctionStmt(this); }

    final Token name;
    final List<Token> params;
    final List<Stmt> body;
    final boolean hasVarArgs;
    final boolean hasVarKwargs;
    final Token varArgsName;
    final Token kwArgsName;
  }

  static class If extends Stmt {
    If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitIfStmt(this); }

    final Expr condition;
    final Stmt thenBranch;
    final Stmt elseBranch;
  }

  static class Import extends Stmt {
    Import(List<Token> pathParts, Token path, Token alias) {
      this.path = path;
      this.pathParts = pathParts;
      this.alias = alias;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitImportStmt(this); }

    final Token path;
    final List<Token> pathParts;
    final Token alias;
  }

  static class Print extends Stmt {
    Print(Expr expression) { this.expression = expression;}

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitPrintStmt(this); }

    final Expr expression;
  }

  static class Puts extends Stmt {
    Puts(Expr expression) { this.expression = expression; }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitPutsStmt(this); }

    final Expr expression;
  }

  static class Return extends Stmt {
    Return(Token keyword, Expr value) {
      this.keyword = keyword;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitReturnStmt(this); }

    final Token keyword;
    final Expr value;
  }

  static class Throw extends Stmt {
    Throw(Expr error) {
      this.error = error;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitThrowStmt(this); }
    final Expr error;
  }

  static class TryCatch extends Stmt {
    TryCatch(List<Stmt> tryBlock, Token errorVar, List<Stmt> catchBlock) {
      this.tryBlock = tryBlock;
      this.errorVar = errorVar;
      this.catchBlock = catchBlock;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitTryCatchStmt(this); }

    final List<Stmt> tryBlock;
    final Token errorVar;
    final List<Stmt> catchBlock;
  }

  static class Var extends Stmt {
    Var(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitVarStmt(this); }

    final Token name;
    final Expr initializer;
  }

  static class While extends Stmt {
    While(Expr condition, Stmt body) {
      this.condition = condition;
      this.body = body;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitWhileStmt(this); }

    final Expr condition;
    final Stmt body;
  }

  abstract <R> R accept(Visitor<R> visitor);
}

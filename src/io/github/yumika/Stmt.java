package io.github.yumika;

import java.util.List;
import java.util.Map;

abstract class Stmt {
  interface Visitor<R> {
    R visitBlockStmt(Block stmt);
    R visitCaseStmt(Case stmt);
    R visitClassStmt(Class stmt);
    R visitDestructuringVarStmt(DestructuringVarStmt stmt);
    R visitExpressionStmt(Expression stmt);
    R visitForStmt(For stmt);
    R visitForEachStmt(ForEach stmt);
    R visitFunctionStmt(Function stmt);
    R visitIfStmt(If stmt);
    R visitImportStmt(Import stmt);
    R visitInterfaceStmt(Interface stmt);
    R visitPrintStmt(Print stmt);
    R visitPutsStmt(Puts stmt);
    R visitReturnStmt(Return stmt);
    R visitThrowStmt(Throw stmt);
    R visitTryCatchStmt(TryCatch stmt);
    R visitTypeDefStmt(TypeDef stmt);
    R visitVarStmt(Var stmt);
    R visitWhileStmt(While stmt);
  }
  public static class Break extends RuntimeException {}
  public static class Continue extends RuntimeException {}

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
          List<Token> interfaces,
          Map<String, Function> methods,
          boolean isAbstract) {
      this.isAbstract = isAbstract;
      this.name = name;
      this.interfaces = interfaces;
      this.superclass = superclass;
      this.methods = methods;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitClassStmt(this); }

    final boolean isAbstract;
    final Token name;
    final Expr.Variable superclass;
    final List<Token> interfaces;
    final Map<String, Stmt.Function> methods;
  }

 static class DestructuringVarStmt extends Stmt {
    DestructuringVarStmt(List<DestructuringField> fields, Expr initializer) {
      this.fields = fields;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitDestructuringVarStmt(this); }

    static class DestructuringField {
      final Token name;
      final Expr defaultValue;

      DestructuringField(Token name, Expr defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
      }
    }

    final List<DestructuringField> fields;
    final Expr initializer;
  }

  static class Expression extends Stmt {
    Expression(Expr expression) { this.expression = expression;}

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitExpressionStmt(this); }

    final Expr expression;
  }

  static class For extends Stmt {
    public For(Stmt initializer, Expr condition, Expr increment, Stmt body) {
      this.initializer = initializer;
      this.condition = condition;
      this.increment = increment;
      this.body = body;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitForStmt(this); }

    final Stmt initializer;
    final Expr condition;
    final Expr increment;
    final Stmt body;
  }

  static class ForEach extends Stmt {
    ForEach(Token variable, Expr iterable, Stmt body) {
      this.variable = variable;
      this.iterable = iterable;
      this.body = body;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitForEachStmt(this); }

    final Token variable;
    final Expr iterable;
    final Stmt body;
  }

  static class Function extends Stmt {
    Function(Token name, List<Expr> decorators, List<Token> params, List<Token> paramTypes,
        Token returnType, List<Stmt> body) {
      this.name = name;
      this.decorators = decorators;
      this.params = params;
      this.paramTypes = paramTypes;
      this.returnType = returnType;
      this.body = body;
      this.hasVarArgs = false;
      this.hasVarKwargs = false;
      this.varArgsName = null;
      this.kwArgsName = null;
    }

    Function(Token name, List<Expr> decorators, List<Token> params, List<Token> paramTypes,
        Token returnType, List<Stmt> body,
        boolean hasVarArgs, boolean hasVarKwargs,
        Token varArgsName, Token kwArgsName) {
      this.name = name;
      this.decorators = decorators;
      this.params = params;
      this.paramTypes = paramTypes;
      this.returnType = returnType;
      this.body = body;
      this.hasVarArgs = hasVarArgs;
      this.hasVarKwargs = hasVarKwargs;
      this.varArgsName = varArgsName;
      this.kwArgsName = kwArgsName;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitFunctionStmt(this); }

    final Token name;
    final List<Expr> decorators;
    final List<Token> params;
    final List<Token> paramTypes;
    final Token returnType;
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

  static class Interface extends Stmt {
    Interface(Token name, List<Stmt.Function> methods) {
      this.name = name;
      this.methods = methods;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitInterfaceStmt(this);
    }
    final Token name;
    final List<Stmt.Function> methods;
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

  static class TypeDef extends Stmt {
    TypeDef(Token name, Expr definition) {
      this.name = name;
      this.definition = definition;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitTypeDefStmt(this);
    }

    final Token name;
    final Expr definition;
  }

  static class Var extends Stmt {
    Var(Token name, Token type, Expr initializer) {
      this.name = name;
      this.type = type;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitVarStmt(this); }

    final Token name;
    final Token type;
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

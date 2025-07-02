package io.github.yumika;

import java.util.List;

abstract class Expr {
  interface Visitor<R> {
    R visitArrayAssignExpr(ArrayAssign expr);
    R visitArrayIndexExpr(ArrayIndex expr);
    R visitAssignExpr(Assign expr);
    R visitBinaryExpr(Binary expr);
    R visitBlockExpr(Block expr);
    R visitCaseExpr(Case expr);
    R visitCallExpr(Call expr);
    R visitCompoundAssignExpr(CompoundAssign expr);
    R visitFunctionExpr(Function expr);
    R visitGetExpr(Get expr);
    R visitGroupingExpr(Grouping expr);
    R visitInterpolatedStringExpr(InterpolatedString expr);
    R visitLambdaExpr(Lambda expr);
    R visitListComprehensionExpr(ListComprehension expr);
    R visitListLiteralExpr(ListLiteral expr);
    R visitLiteralExpr(Literal expr);
    R visitLogicalExpr(Logical expr);
    R visitMatchExpr(Match expr);
    R visitNewTypedArrayExpr(NewTypedArray expr);
    R visitNullCoalesceExpr(NullCoalesce expr);
    R visitObjectLiteralExpr(ObjectLiteral expr);
    R visitOptionalGetExpr(OptionalGet expr);
    R visitPostfixExpr(Postfix expr);
    R visitPrefixExpr(Prefix expr);
    R visitSetExpr(Set expr);
    R visitSpreadExpr(Spread expr);
    R visitSuperExpr(Super expr);
    R visitThisExpr(This expr);
    R visitUnaryExpr(Unary expr);
    R visitUndefinedExpr(Undefined expr);
    R visitVariableExpr(Variable expr);
  }

  static class ListLiteral extends Expr {
    ListLiteral(List<Expr> elements) {
      this.elements = elements;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitListLiteralExpr(this); }

    final List<Expr> elements;
  }

  static class Spread extends Expr {
    Spread(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitSpreadExpr(this); }

    final Expr expression;
  }

  static class ArrayIndex extends Expr {

    ArrayIndex(Expr array, Token bracket, Expr index) {
      this.array = array;
      this.bracket = bracket;
      this.index = index;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitArrayIndexExpr(this); }

    final Expr array;
    final Token bracket;
    final Expr index;
  }

  static class ArrayAssign extends Expr {

    ArrayAssign(Token name, Expr array, Expr index, Expr value) {
      this.name = name;
      this.array = array;
      this.index = index;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitArrayAssignExpr(this); }

    final Token name;
    final Expr array;
    final Expr index;
    final Expr value;
  }

  static class Assign extends Expr {
    Assign(Token name, Expr value) {
      this.name = name;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitAssignExpr(this); }

    final Token name;
    final Expr value;
  }

  static class Binary extends Expr {
    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitBinaryExpr(this); }

    final Expr left;
    final Token operator;
    final Expr right;
  }

  static class Block extends Expr {
    Block(List<Stmt> statements) {
      this.statements = statements;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitBlockExpr(this); }

    final List<Stmt> statements;
  }

  static class Call extends Expr {
    Call(Expr callee, Token paren, List<Expr> arguments) {
      this.callee = callee;
      this.paren = paren;
      this.arguments = arguments;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitCallExpr(this); }

    final Expr callee;
    final Token paren;
    final List<Expr> arguments;
  }

  static class Case extends Expr {
    Case(Expr expression, List<WhenClause> whenClauses, Expr elseBranch) {
      this.expression = expression;
      this.whenClauses = whenClauses;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitCaseExpr(this); }

    final Expr expression;
    final List<WhenClause> whenClauses;
    final Expr elseBranch;

    static class WhenClause {
      WhenClause(Expr match, Expr result) {
        this.match = match;
        this.result = result;
      }

      final Expr match;
      final Expr result;
    }
  }

  static class CompoundAssign extends Expr {
    CompoundAssign(Token name, Token operator, Expr value) {
      this.name = name;
      this.operator = operator;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitCompoundAssignExpr(this); }

    final Token name;
    final Token operator;
    final Expr value;
  }

  static class Function extends Expr {
    Function(List<Expr> decorators, List<Token> params, List<Token> paramTypes, Token returnType, List<Stmt> body) {
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

    Function(List<Expr> decorators, List<Token> params, List<Token> paramTypes,
        Token returnType, List<Stmt> body,
        boolean hasVarArgs, boolean hasVarKwargs,
        Token varArgsName, Token kwArgsName) {
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
    <R> R accept(Visitor<R> visitor) { return visitor.visitFunctionExpr(this); }

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

  static class Get extends Expr {
    Get(Expr object, Token name) {
      this.object = object;
      this.name = name;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitGetExpr(this); }

    final Expr object;
    final Token name;
  }

  static class Grouping extends Expr {
    Grouping(Expr expression) { this.expression = expression;}

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitGroupingExpr(this); }

    final Expr expression;
  }

  static class InterpolatedString extends Expr {
    InterpolatedString(List<Expr> parts) {
      this.parts = parts;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitInterpolatedStringExpr(this); }
    final List<Expr> parts;
  }

  static class Lambda extends Expr {
    Lambda(List<Token> params, List<Token> paramTypes, Token returnType, Expr body) {
      this.params = params;
      this.paramTypes = paramTypes;
      this.returnType = returnType;
      this.body = body;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitLambdaExpr(this); }

    final List<Token> params;
    final List<Token> paramTypes;
    final Token returnType;
    final Expr body;
  }

  static class ListComprehension extends Expr {

    ListComprehension(Expr elementExpr, Token variable, Expr iterable, Expr condition) {
      this.elementExpr = elementExpr;
      this.variable = variable;
      this.iterable = iterable;
      this.condition = condition;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitListComprehensionExpr(this); }

    final Expr elementExpr;
    final Token variable;
    final Expr iterable;
    final Expr condition;
  }

  static class Literal extends Expr {
    Literal(Object value) { this.value = value; }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitLiteralExpr(this); }
    final Object value;
  }

  static class Logical extends Expr {
    Logical(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitLogicalExpr(this); }

    final Expr left;
    final Token operator;
    final Expr right;
  }

  static class Match extends Expr {
    Match(Expr value, List<MatchCase> cases) {
      this.value = value;
      this.cases = cases;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitMatchExpr(this); }

    final Expr value;
    final List<MatchCase> cases;
  }

  static class MatchCase {
    MatchCase(Expr pattern, Expr body, boolean isElse) {
      this.pattern = pattern;
      this.body = body;
      this.isElse = isElse;
    }

    final Expr pattern;
    final Expr body;
    final boolean isElse;
  }

  static class NewTypedArray extends Expr {
    NewTypedArray(Token type, Expr size) {
      this.type = type;
      this.size = size;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitNewTypedArrayExpr(this); }
    final Token type;
    final Expr size;
  }

  static class NullCoalesce extends Expr {
    NullCoalesce(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitNullCoalesceExpr(this); }

    final Expr left;
    final Token operator;
    final Expr right;
  }

  static class ObjectLiteral extends Expr {
    ObjectLiteral(List<Property> properties) {
      this.properties = properties;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitObjectLiteralExpr(this); }

    final List<Property> properties;

    interface Property {}

    static class Pair implements Property {
      Pair(Token key, Expr value) {
        this.key = key;
        this.value = value;
      }

      final Token key;
      final Expr value;
    }

    static class Spread implements Property {
      Spread(Expr expression) {
        this.expression = expression;
      }
      final Expr expression;
    }

    static class Accessor implements Property {
      Accessor(Token kind, Token name, Expr.Function function) {
        this.kind = kind;
        this.name = name;
        this.function = function;
      }

      final Token kind;
      final Token name;
      final Expr.Function function;
    }
  }

  static class OptionalGet extends Expr {
    OptionalGet(Expr object, Token name) {
      this.object = object;
      this.name = name;
    }

    <R> R accept(Visitor<R> visitor) { return visitor.visitOptionalGetExpr(this); }

    final Expr object;
    final Token name;
  }

  static class Postfix extends Expr {
    Postfix(Expr.Variable variable, Token operator) {
      this.variable = variable;
      this.operator = operator;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitPostfixExpr(this); }

    final Expr.Variable variable;
    final Token operator;
  }

  static class Prefix extends Expr {
    Prefix(Expr.Variable variable, Token operator) {
      this.variable = variable;
      this.operator = operator;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitPrefixExpr(this); }
    final Expr.Variable variable;
    final Token operator;
  }

  static class Set extends Expr {
    Set(Expr object, Token name, Expr value) {
      this.object = object;
      this.name = name;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitSetExpr(this); }

    final Expr object;
    final Token name;
    final Expr value;
  }

  static class Super extends Expr {
    Super(Token keyword, Token method) {
      this.keyword = keyword;
      this.method = method;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitSuperExpr(this); }

    final Token keyword;
    final Token method;
  }

  static class This extends Expr {
    This(Token keyword) { this.keyword = keyword; }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitThisExpr(this); }

    final Token keyword;
  }

  static class Unary extends Expr {
    Unary(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitUnaryExpr(this); }

    final Token operator;
    final Expr right;
  }

  static class Undefined extends Expr {
    Undefined() {
    }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitUndefinedExpr(this); }

    @Override
    public String toString() {
      return "undefined";
    }
  }

  static class Variable extends Expr {
    Variable(Token name) { this.name = name; }

    @Override
    <R> R accept(Visitor<R> visitor) { return visitor.visitVariableExpr(this); }

    final Token name;

    @Override
    public String toString() {
      return super.toString() + "_<Variable: " + name.lexeme + "(" + name.literal + ")>";
    }
  }

  abstract <R> R accept(Visitor<R> visitor);
}

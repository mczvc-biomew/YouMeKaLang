package io.github.yumika;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static io.github.yumika.TokenType.*;

// Front-end
class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) { this.tokens = tokens; }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Expr expression() {
    return assignment();
  }

  private Stmt declaration() {
    try {
      if (match(CLASS)) return classDeclaration();

      if (match(FUN)) return function("function");

      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(IDENTIFIER, "Expect class name.");

    Expr.Variable superclass = null;
    if (match(LESS)) {
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }

    consume(LEFT_BRACE, "Expect '{' before class body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }

    consume(RIGHT_BRACE, "Expect '}' after class body.");

    return new Stmt.Class(name, superclass, methods);
  }

  private Stmt statement() {
    if (match(FOR)) return forStatement();

    if (match(IF)) return ifStatement();

    if (match(PRINT)) return printStatement();

    if (match(RETURN)) return returnStatement();

    if (match(WHILE)) return whileStatement();

    if (match(LEFT_BRACE)) return new Stmt.Block(block());

    return expressionStatement();
  }

  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();

    if (increment != null) {
      body = new Stmt.Block(
          Arrays.asList(
              body,
              new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");

    return new Stmt.Var(name, initializer);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after condition.");
    Stmt body = statement();

    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();

    boolean hasVarArgs = false;
    boolean hasVarKwargs = false;
    Token varArgsName = null;
    Token kwArgsName = null;

    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Maximum of 255 parameters.");
        }

        if (match(STAR)) {
          if (match(STAR)) {
            hasVarArgs = true;
            varArgsName = consume(IDENTIFIER, "Expect name for variable arguments.");
            parameters.add(varArgsName);
          } else {
            hasVarArgs = true;
            kwArgsName = consume(IDENTIFIER, "Expect name for keyword arguments.");
            parameters.add(kwArgsName);
          }
        } else {
          parameters.add(
              consume(IDENTIFIER, "Expect parameter name."));
        }
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body, hasVarArgs, hasVarKwargs, varArgsName, kwArgsName );
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.ArrayIndex) {
        Expr array = ((Expr.ArrayIndex)expr).array;
        if (!(array instanceof Expr.Variable)) {
          throw new RuntimeError(equals, "Expect array variable.");
        }
        Token name = ((Expr.Variable)array).name;
        Expr index = ((Expr.ArrayIndex)expr).index;
        return new Expr.ArrayAssign(name, array, index, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get)expr;
        return new Expr.Set(get.object, get.name, value);
      }
      error(equals, "Invalid assignment target.");
    }
    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();

    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Maximum of 255 arguments.");
        }
        arguments.add(expression());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN,
        "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments);
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(LEFT_BRACKET)) {
        Expr index = expression();
        Token bracket = consume(RIGHT_BRACKET, "Expect ']' after index.");
        expr = new Expr.ArrayIndex(expr, bracket, index);
      } else if (match(DOT)) {
        Token name = consume(IDENTIFIER,
            "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }
    return expr;
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NULL)) return new Expr.Literal(null);
    if (match(UNDEFINED)) return new Expr.Literal.Undefined();

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER,
          "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }

    if (match(THIS)) return new Expr.This(previous());

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(LEFT_BRACKET)) {
      Expr element = expression();

      if (match(FOR)) {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Token variable = consume(IDENTIFIER, "Expect identifier");
        consume(IN, "Expect 'in'");
        Expr iterable = expression();
        consume(RIGHT_PAREN, "Expect ')'");
        Expr condition = null;
        if (match(IF)) {
          condition = expression();
        }
        consume(RIGHT_BRACKET, "Expect ']' after list comprehension");

        return new Expr.ListComprehension(element, variable, iterable, condition);
      } else {
        List<Expr> elements = new ArrayList<>();
        if (!check(RIGHT_BRACKET)) {
          do {
            element = element != null ? element : expression();
            elements.add(element);
            element = null;
          } while (match(COMMA));
        }

        consume(RIGHT_BRACKET, "Expect ']' after array elements.");
        return new Expr.ArrayLiteral(elements);
      }
    }
    if (match(LEFT_BRACE)) {
      if (match(IDENTIFIER) || match(STRING)) {
        return objectLiteral();
      }
    }

    throw error(peek(), "Expect expression.");
  }

  private Expr objectLiteral() {
    Map<String, Expr> properties = new HashMap<>();
    Token key = previous();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (key == null) {
        if (match(IDENTIFIER) || match(STRING)) {
          key = previous();
        } else {
          throw new RuntimeError(key, "Expect property name.");
        }
      }
      consume(COLON, "Expect ':' after property name.");
      Expr value = expression();
      properties.put(key.lexeme, value);
      key = null;
      if (!match(COMMA))
        break;
    }
    consume(RIGHT_BRACE, "Expect '}' after object literal.");
    return new Expr.ObjectLiteral(properties);
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() { return peek().type == EOF; }

  private Token peek() { return tokens.get(current); }

  private Token previous() { return tokens.get(current - 1); }

  private ParseError error(Token token, String message) {
    YouMeKa.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }

}

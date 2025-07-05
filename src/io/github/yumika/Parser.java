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
    return pipeline();
  }

  private Expr pipeline() {
    Expr expr =  matchExpr();

    while(match(PIPE_GREATER)) {
      Token operator = previous();
      Expr right = matchExpr();
      expr = new Expr.Pipeline(expr, right);
    }

    return expr;
  }

  private Stmt declaration() {
    try {
      if (match(ABSTRACT) || match(CLASS)) return classDeclaration();

      if (match(INTERFACE)) return interfaceDeclaration();

      if (check(AT) || match(FUN)) return functionStatement("function");

      if (match(VAR)) return varDeclaration();

      if (match(TYPE)) return typeDefinition();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    boolean isAbstract = previous().lexeme.equals("abstract");
    if (isAbstract) consume(CLASS, "expect abstract class.");

    Token name = consume(IDENTIFIER, "Expect class name.");

    Expr.Variable superclass = null;
    if (match(LESS)) {
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }

    List<Token> interfaces = new ArrayList<>();
    if (match(GREATER)) {
      do {
        interfaces.add(consume(IDENTIFIER, "Expect interface name."));
      } while (match(COMMA));
    }

    consume(LEFT_BRACE, "Expect '{' before class body.");

    Map<String, Stmt.Function> methods = new HashMap<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      Stmt.Function funStatement = functionStatement("method");
      methods.put(funStatement.name.lexeme, funStatement);
    }

    consume(RIGHT_BRACE, "Expect '}' after class body.");

    return new Stmt.Class(name, superclass, interfaces, methods, isAbstract);
  }

  private Stmt statement() {
    if (match(IMPORT)) return importStatement();

    if (match(FOR)) return forStatement();

    if (match(IF)) return ifStatement();

    if (match(CASE)) return caseStatement();

    if (match(PRINT)) return printStatement();

    if (match(PUTS)) return putsStatement();

    if (match(RETURN)) return returnStatement();

    if (match(WHILE)) return whileStatement();

    if (match(LEFT_BRACE)) return new Stmt.Block(block());

    if (match(TRY)) return tryStatement();
    if (match(THROW)) return throwStatement();

    return expressionStatement();
  }

  private Stmt caseStatement() {
    Expr caseExpr = expression();
    consume(LEFT_BRACE, "Expect '{' after case statement.");

    List<Stmt.Case.WhenClause> whenClauses = new ArrayList<>();
    Stmt elseBranch = null;

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(WHEN)) {
        Expr matchExpr = expression();
        consume(ARROW, "Expect '=>' after 'when' condition.");
        Stmt body = statement();
        whenClauses.add(new Stmt.Case.WhenClause(matchExpr, body));
      } else if (match(ELSE)) {
        consume(ARROW, "Expect 'when' or 'else' in case.");
        elseBranch = statement();
      } else {
        throw error(peek(), "Expect 'when' or 'else' in case.");
      }
    }

    consume(RIGHT_BRACE, "Expect '}' after case block.");
    return new Stmt.Case(caseExpr, whenClauses, elseBranch);

  }

  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      if (match(TokenType.LEFT_BRACE)) {
        return destructuringVarStmt();
      }

      Token name = consume(IDENTIFIER, "Expect loop variable name.");

      if (match(IN)) {
        Expr iterable = expression();
        consume(RIGHT_PAREN, "Expect ')' after iterable.");
        Stmt body = statement();
        return new Stmt.ForEach(name, iterable, body);
      }

      Token typeAnnotation = null;
      if (match(COLON)) {
        typeAnnotation = consume(IDENTIFIER, "Expect type name after ':'.");
      }

      Expr initializerExpr = null;
      if (match(EQUAL)) {
        initializerExpr = expression();
      }

      consume(SEMICOLON, "Expect ';' after variable declaration.");

      initializer = new Stmt.Var(name, typeAnnotation, initializerExpr);
      return finishClassicForLoop(initializer);
    } else {
      initializer = expressionStatement();
    }

    return finishClassicForLoop(initializer);
  }

  private Stmt destructuringVarStmt() {
    // object destructuring: var {a, b, c = "default"} = expr;
    List<Stmt.DestructuringVarStmt.DestructuringField> fields = parseDestructuringPattern();
    consume(TokenType.EQUAL, "Expect '=' after destructuring pattern.");
    Expr initializer = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.DestructuringVarStmt(fields, initializer);
  }

  private Stmt finishClassicForLoop(Stmt initializer) {

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

  private Stmt importStatement() {
    List<Token> pathParts = new ArrayList<>();
    Token module = consume(IDENTIFIER, "Expect module name.");
    Token alias = module;
    Token path = module;

    pathParts.add(module);

    while (match(DOT)) {
      Token next = consume(IDENTIFIER,
          "Expect identifier after '.'.");
      pathParts.add(next);
      path = new Token(IDENTIFIER, path.lexeme + "." + next.lexeme, null, path.line);
    }

    if (match(AS)) {
      alias = consume(IDENTIFIER, "Expect alias after 'as'.");
    }

    consume(SEMICOLON, "Expect ';' import statement.");
    return new Stmt.Import(pathParts, path, alias);
  }

  private Stmt interfaceDeclaration() {
    Token name = consume(IDENTIFIER, "Expect interface name.");
    consume(LEFT_BRACE, "Expect '{' after interface name.");

    List<Stmt.Function> methods = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(functionSignature("method"));
    }

    consume(RIGHT_BRACE, "Expect '}' after interface body.");
    return new Stmt.Interface(name, methods);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt putsStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Puts(value);
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

  private Stmt throwStatement() {
    Expr error = expression();
    consume(SEMICOLON, "Expect ';' after throw.");
    return new Stmt.Throw(error);
  }

  private Stmt tryStatement() {
    consume(LEFT_BRACE,
        "Expect '{' after 'try'.");
    List<Stmt> tryBlock = block();

    consume(CATCH,
        "Expect 'catch' after 'try' block.");
    consume(LEFT_PAREN,
        "Expect '(' after 'catch'.");
    Token errorVar = consume(IDENTIFIER,
        "Expect error variable name.");
    consume(RIGHT_PAREN,
        "Expect ')' after error variable.");
    consume(LEFT_BRACE,
        "Expect '{' before 'catch' block.");
    List<Stmt> catchBlock = block();

    return new Stmt.TryCatch(tryBlock, errorVar, catchBlock);
  }

  private Stmt typeDefinition() {
    Token name = consume(IDENTIFIER, "Expect type name.");
    consume(EQUAL, "Expect '=' after type name.");

    Expr structure = parseTypeStructure();
    consume(SEMICOLON, "Expect ';' after type definition.");

    return new Stmt.TypeDef(name, structure);
  }

  private Expr parseTypeStructure() {
    return parsePattern();
  }

  private Stmt variableDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Token typeAnnotation = null;
    if (match(COLON)) {
      typeAnnotation = consume(IDENTIFIER, "Expect type name after ':'.");
    }

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");

    return new Stmt.Var(name, typeAnnotation, initializer);
  }

  private Stmt varDeclaration() {
    if (match(TokenType.LEFT_BRACE)) {
      // object destructuring: var {a, b, c = "default"} = expr;
      List<Stmt.DestructuringVarStmt.DestructuringField> fields = parseDestructuringPattern();
      consume(TokenType.EQUAL, "Expect '=' after destructuring pattern.");
      Expr initializer = expression();
      consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
      return new Stmt.DestructuringVarStmt(fields, initializer);
    }

    return variableDeclaration();
  }

  private List<Stmt.DestructuringVarStmt.DestructuringField> parseDestructuringPattern() {
    List<Stmt.DestructuringVarStmt.DestructuringField> fields = new ArrayList<>();
    do {
      Token name = consume(IDENTIFIER, "Expect identifier in destructuring pattern.");
      Expr defaultValue = null;

      if (match(EQUAL)) {
        defaultValue = expression();
      }
      fields.add(new Stmt.DestructuringVarStmt.DestructuringField(name, defaultValue));
    } while (match(COMMA));

    consume(RIGHT_BRACE, "Expect '}' after destructuring pattern.");
    return fields;
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

  private Stmt.Function functionStatement(String kind) {
    List<Expr> decorators = new ArrayList<>();
    boolean decorated = false;

    while (match(AT)) {
      decorators.add(expression());
      decorated = true;
    }

    if (decorated && !match(FUN)) {
      error(peek(), "Expect 'fun'.");
    }

    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

    List<Token> parameters = new ArrayList<>();
    List<Token> paramTypes = new ArrayList<>();
    List<Expr> defaultValues = new ArrayList<>();

    boolean hasVarArgs = false;
    boolean hasVarKwargs = false;
    Token varArgsName = null;
    Token kwArgsName = null;

    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Maximum of 255 parameters.");
        }

        if (match(STAR_STAR)) {
          hasVarKwargs = true;
          kwArgsName = consume(IDENTIFIER, "Expect name for keyword arguments.");
//          parameters.add(kwArgsName);
        } else if (match(STAR)) {
            hasVarArgs = true;
            varArgsName = consume(IDENTIFIER, "Expect name for variable arguments.");
//            parameters.add(varArgsName);
        } else {
          parameters.add(
              consume(IDENTIFIER, "Expect parameter name."));

          if (match(COLON)) {
            Token type = consume(IDENTIFIER, "Expect type name.");
            paramTypes.add(type);
          } else {
            paramTypes.add(null);
          }

          if (match(EQUAL)) {
            defaultValues.add(expression());
          } else {
            defaultValues.add(null);
          }
        }
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    Token returnType = null;
    if (match(COLON)) {
      returnType = consume(IDENTIFIER, "Expect return type.");
    }

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, decorators,
        parameters, paramTypes,
        returnType, body,
        hasVarArgs, hasVarKwargs,
        varArgsName, kwArgsName );
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
    Expr expr = postfix();

    if (match(EQUAL, MINUS_EQUAL, PLUS_EQUAL)) {
      Token operator = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable varExpr) {
        if (operator.type == EQUAL) {
          return new Expr.Assign(varExpr.name, value);
        } else {
          return new Expr.CompoundAssign(varExpr.name, operator, value);
        }
      } else if (expr instanceof Expr.ArrayIndex) {
        Expr array = ((Expr.ArrayIndex)expr).array;
        if (!(array instanceof Expr.Variable)) {
          throw new RuntimeError(operator, "Expect array variable.");
        }
        Token name = ((Expr.Variable)array).name;
        Expr index = ((Expr.ArrayIndex)expr).index;
        return new Expr.ArrayAssign(name, array, index, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get)expr;
        return new Expr.Set(get.object, get.name, value);
      }
      error(operator, "Invalid assignment target.");
    }
    return expr;
  }

  private Expr postfix() {
    Expr expr = or();

    if (match(PLUS_PLUS, MINUS_MINUS)) {
      Token operator = previous();
      if (expr instanceof Expr.Variable) {
        return new Expr.Postfix((Expr.Variable) expr, operator);
      } else {
        throw error(operator, "Only variables can be incremented or decremented.");
      }
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
    Expr expr = inclusion();

    while (match(AND)) {
      Token operator = previous();
      Expr right = inclusion();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr inclusion() {
    Expr expr = equality();

    while (match(IN)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Binary(expr, operator, right);
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
    if (match(BANG, NOT, MINUS, MINUS_MINUS, PLUS_PLUS)) {
      Token operator = previous();
      Expr right = unary();

      if (operator.type == MINUS_MINUS || operator.type == PLUS_PLUS) {
        if (right instanceof Expr.Variable) {
          return new Expr.Prefix((Expr.Variable) right, operator);
        } else {
          throw error(operator, "Only variables can be incremented or decremented.");
        }
      }
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    Map<String, Expr> keywordArgs = new HashMap<>();

    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Maximum of 255 arguments.");
        }
        if (check(IDENTIFIER) && peekNext().type == COLON) {
          Token key = consume(IDENTIFIER, "Expect keyword argument.");
          consume(COLON, "Expect ':' after keyword name.");
          Expr value = expression();
          keywordArgs.put(key.lexeme, value);
        } else {
          arguments.add(expression());
        }
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN,
        "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments, keywordArgs);
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
        if (check(GET)) {
          Token name = consume(GET, "Expect get.");
          expr = new Expr.Get(expr, name);
        } else {
          Token name = consume(IDENTIFIER,
              "Expect property name after '.'.");
          expr = new Expr.Get(expr, name);
        }
      } else if (match(QUESTION_DOT)) {
        Token name = consume(IDENTIFIER, "Expect property name after '?.'.");
        expr = new Expr.OptionalGet(expr, name);
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

    if (match(FUN)) return functionExpression("function");

    if (match(NEW)) return newObject();

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(TEMPLATE_STRING)) {
      List<Object> parts = (List<Object>) previous().literal;
      List<Expr> expressions = new ArrayList<>();

      for (Object part : parts) {
        if (part instanceof String str) {
          expressions.add(new Expr.Literal(str));
        } else if (part instanceof Token token) {
          expressions.add(parseTemplateExpr(token));
        }
      }
      return new Expr.InterpolatedString(expressions);
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

    if (check(PRINT) && isInExpressionContext()) {
      Token fakeIdentifier = new Token(TokenType.IDENTIFIER, "print", null, peek().line);
      advance(); // consume 'print'
      return new Expr.Variable(fakeIdentifier);
    }

    if (check(PUTS) && isInExpressionContext()) {
      Token fakeIdentifier = new Token(TokenType.IDENTIFIER, "puts", null, peek().line);
      advance(); // consume 'puts'
      return new Expr.Variable(fakeIdentifier);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(LEFT_BRACKET)) {
      if (match(RIGHT_BRACKET)) {
        return new Expr.ListLiteral(null);
      }
      return listLiteral();
    }

    if (match(LEFT_BRACE)) {
      if (match(RIGHT_BRACE)) {
        return new Expr.ObjectLiteral(null);
      }
      return objectLiteral();
    }

    if (match(CASE)) return caseExpression();

    if (check(PIPE)) {
      return lambda();
    }

    if (match(YIELD)) {
      return yieldExpression();
    }

    throw error(peek(), "Expect expression.");
  }

  private boolean isInExpressionContext() {
    return true;
  }

  private Expr caseExpression() {
    Expr caseExpr = expression();
    consume(LEFT_BRACE, "Expect '{' after 'case'.");

    List<Expr.Case.WhenClause> whenClauses = new ArrayList<>();
    Expr elseBranch = null;

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(WHEN)) {
        Expr match = expression();
        consume(ARROW, "Expect '=>' after 'when'.");
        Expr result = expression();
        consume(SEMICOLON, "Expect ';' after case branch.");
        whenClauses.add(new Expr.Case.WhenClause(match, result));
      } else if (match(ELSE)) {
        consume(ARROW, "Expect '=>' after 'else'.");
        elseBranch = expression();
        consume(SEMICOLON, "Expect ';' after else branch.");
      } else {
        throw error(peek(), "Expect 'when' or 'else' in case.");
      }
    }

    consume(RIGHT_BRACE, "Expect '}' after case expression.");
    return new Expr.Case(caseExpr, whenClauses, elseBranch);
  }

  private Expr functionExpression(String kind) {
    List<Expr> decorators = new ArrayList<>();

    while (match(AT)) {
      decorators.add(expression());
    }
    consume(LEFT_PAREN, "Expect '(' after '" + kind + "'.");
    List<Token> parameters = new ArrayList<>();
    List<Token> paramTypes = new ArrayList<>();
    List<Expr> defaultValues = new ArrayList<>();

    if (!check(RIGHT_PAREN)) {
      do {
        parameters.add(consume(IDENTIFIER,
            "Expect parameter name."));

        if (match(COLON)) {
          Token type = consume(IDENTIFIER, "Expect type name.");
          paramTypes.add(type);
        } else {
          paramTypes.add(null);
        }

        if (match(EQUAL)) {
          defaultValues.add(expression());
        } else {
          defaultValues.add(null);
        }
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    Token returnType = null;
    if (match(COLON)) {
      returnType = consume(IDENTIFIER, "Expect return type.");
    }

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Expr.Function(decorators, parameters, paramTypes, returnType, body);
  }

  private Stmt.Function functionSignature(String kind) {
    Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + " name.");
    consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + " name.");

    List<Token> params = new ArrayList<>();
    List<Token> paramTypes = new ArrayList<>();
    boolean hasVarArgs = false;
    boolean hasVarKwargs = false;
    Token varArgsName = null;
    Token kwArgsName = null;

    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (match(TokenType.STAR_STAR)) { // **kwargs
          hasVarKwargs = true;
          kwArgsName = consume(TokenType.IDENTIFIER, "Expect kwargs name.");
          break;
        } else if (match(TokenType.STAR)) { // *args
          hasVarArgs = true;
          varArgsName = consume(TokenType.IDENTIFIER, "Expect varargs name.");
        } else {
          Token param = consume(TokenType.IDENTIFIER, "Expect parameter name.");
          Token type = null;
          if (match(TokenType.COLON)) {
            type = consume(TokenType.IDENTIFIER, "Expect type name after ':'.");
          }
          params.add(param);
          paramTypes.add(type);
        }
      } while (match(TokenType.COMMA));
    }

    consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");

    Token returnType = null;
    if (match(TokenType.COLON)) {
      returnType = consume(TokenType.IDENTIFIER, "Expect return type after ':'.");
    }

    consume(TokenType.SEMICOLON, "Expect ';' after " + kind + " signature.");

    return new Stmt.Function(
        name,
        new ArrayList<>(), // decorators: none in interface
        params,
        paramTypes,
        returnType,
        new ArrayList<>(), // body is empty in signature
        hasVarArgs,
        hasVarKwargs,
        varArgsName,
        kwArgsName
    );
  }

  private Expr lambda() {
    List<Token> parameters = new ArrayList<>();
    List<Token> paramTypes = new ArrayList<>();
    List<Expr> defaultValues = new ArrayList<>();
    Token returnType = null;
    // Handle |x| ...
    if (match(PIPE)) {
      if (!check(PIPE)) {
        do {
          if (parameters.size() >= 255) {
            error(peek(), "Maximum of 255 parameters.");
          }
          parameters.add(consume(IDENTIFIER, "Expect parameter name."));

          if (match(COLON)) {
            Token type = consume(IDENTIFIER, "Expect type name.");
            paramTypes.add(type);
          } else {
            paramTypes.add(null);
          }

          if (match(EQUAL)) {
            defaultValues.add(expression());
          } else {
            defaultValues.add(null);
          }
        } while (match(COMMA));
      }
      consume(PIPE, "Expect '|' after parameters.");

      if (match(COLON)) {
        returnType = consume(IDENTIFIER, "Expect return type.");
      }
    } else {
      throw error(peek(), "Expect lambda parameters.");
    }
    Expr body;
    if (match(LEFT_BRACE)) {
      List<Stmt> block = block();
      body = new Expr.Block(block);
    } else {
      body = expression();
    }
    return new Expr.Lambda(parameters, paramTypes, returnType, body);
  }

  private Expr matchExpr() {
    if (!match(MATCH)) return nullCoalesce();

    Expr matchedValue = expression();
    consume(LEFT_BRACE, "Expect '{' after match value.");

    List<Expr.MatchCase> cases = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      boolean isElse = match(ELSE);
      if (!isElse) consume(WHEN, "Expect 'when' or 'else'.");

      Expr pattern = isElse ? null : parsePattern();
      consume(ARROW, "Expect '=>' after pattern");
      Expr body = expression();
      consume(SEMICOLON, "Expect ';' after match case.");

      cases.add(new Expr.MatchCase(pattern, body, isElse));
    }

    consume(RIGHT_BRACE, "Expect '}' after match cases.");
    return new Expr.Match(matchedValue, cases);
  }

  private Expr parsePattern() {
    // Match object pattern: { key, key2 }
    if (match(TokenType.LEFT_BRACE)) {
      List<Expr.ObjectLiteral.Property> fields = new ArrayList<>();
      if (!check(TokenType.RIGHT_BRACE)) {
        do {
          Token name = consume(TokenType.IDENTIFIER, "Expect identifier in object pattern.");
          // Pattern bindings: { x } → treat as variable ref
          Expr value;
          if (match(COLON)) {
            value = parsePattern();
          } else {
            value = new Expr.Variable(name);
          }
          fields.add(new Expr.ObjectLiteral.Pair(name, value));
        } while (match(TokenType.COMMA));
      }
      consume(TokenType.RIGHT_BRACE, "Expect '}' after object pattern.");
      return new Expr.ObjectLiteral(fields);
    }

    // Match array pattern: [1, x, _]
    if (match(TokenType.LEFT_BRACKET)) {
      List<Expr> elements = new ArrayList<>();
      if (!check(TokenType.RIGHT_BRACKET)) {
        do {
          elements.add(parsePattern());  // Support nested patterns
        } while (match(TokenType.COMMA));
      }
      consume(TokenType.RIGHT_BRACKET, "Expect ']' after list pattern.");
      return new Expr.ListLiteral(elements);
    }

    // Fallback to variable or literal
    return assignment();  // handles numbers, strings, identifiers
  }


  private Expr newObject() {
    Token klass = consume(IDENTIFIER, "Expect class name");
    if (check(LEFT_BRACKET)) {
      return newTypedArrayExpression();
    } else {
      // @TODO: implement new class operator
      return null;
    }
  }

  private Expr newTypedArrayExpression() {
    Token type = previous();// consume(IDENTIFIER, "Expect type name after 'new'.");
    consume(LEFT_BRACKET, "Expect '[' after type.");
    Expr sizeExpr = expression();
    consume(RIGHT_BRACKET, "Expect ']' after array size.");

    return new Expr.NewTypedArray(type, sizeExpr);
  }

  private Expr nullCoalesce() {
    Expr expr = assignment();

    while (match(QUESTION_QUESTION)) {
      Token operator = previous();
      Expr right = assignment();
      expr = new Expr.NullCoalesce(expr, operator, right);
    }

    return expr;
  }

  private Expr listComprehension() {
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
      elements.add(element);
      if (!check(RIGHT_BRACKET)) {
        do {
          if (match((DOT_DOT_DOT))) {
//            Expr spreadExpr = expression();
            if (element == null) {
              elements.add(new Expr.Spread(expression()));
            }
          } else {
            if (element == null) {
              elements.add(expression());
            }
          }
          element = null;
        } while (match(COMMA));
      }

      consume(RIGHT_BRACKET, "Expect ']' after array elements.");
      return new Expr.ListLiteral(elements);
    }
  }

  private Expr listLiteral() {
    if (check(DOT_DOT_DOT)) {
      List<Expr> elements = new ArrayList<>();
      if (!check(RIGHT_BRACKET)) {
        do {
          if (match((DOT_DOT_DOT))) {
            Expr spreadExpr = expression();
            elements.add(new Expr.Spread(spreadExpr));
          } else {
            elements.add(expression());
          }
        } while (match(COMMA));
      }

      consume(RIGHT_BRACKET, "Expect ']' after array elements.");
      return new Expr.ListLiteral(elements);
    } else {
      return listComprehension();
    }

  }

  private Expr objectLiteral() {
    List<Expr.ObjectLiteral.Property> properties = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(DOT_DOT_DOT)) {
        Expr spreadExpr = expression();
        properties.add(new Expr.ObjectLiteral.Spread(spreadExpr));
      } else if (match(IDENTIFIER) || match(STRING)) {
        Token key = previous();
        consume(COLON, "Expect ':' after property name.");
        Expr value = expression();
        properties.add(new Expr.ObjectLiteral.Pair(key, value));
      } else if (match(GET, SET)) {
        Token accessorType = previous();
        Token name = consume(IDENTIFIER,
            "Expect property name after 'get' or 'set'");
        consume(LEFT_PAREN, "Expect '(' after property name.");

        List<Token> parameters = new ArrayList<>();
        List<Token> paramTypes = new ArrayList<>();
        List<Expr> defaultValues = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
          do {
            parameters.add(consume(IDENTIFIER, "Expect parameter name."));

            if (match(COLON)) {
              Token type = consume(IDENTIFIER, "Expect type name.");
              paramTypes.add(type);
            } else {
              paramTypes.add(null);
            }

            if (match(EQUAL)) {
              defaultValues.add(expression());
            } else {
              defaultValues.add(null);
            }
          } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        Token returnType = null;
        if (match(COLON)) {
          returnType = consume(IDENTIFIER, "Expect return type.");
        }

        consume(LEFT_BRACE, "Expect '{' before accessor body.");
        List<Stmt> body = block();

        Expr.Function function = new Expr.Function(null, parameters, paramTypes, returnType, body);
        properties.add(new Expr.ObjectLiteral.Accessor(accessorType, name, function));
      } else {
        throw error(peek(), "Expect property name.");
      }
      if (!match(COMMA))
        break;
    }
    consume(RIGHT_BRACE, "Expect '}' after object literal.");
    return new Expr.ObjectLiteral(properties);
  }

  private Expr parseTemplateExpr(Token token) {
    String exprSource = token.lexeme;
    Scanner scanner = new Scanner(exprSource);
    List<Token> innerTokens = scanner.scanTokens();

    Parser subParser = new Parser(innerTokens);
    return subParser.expression();
  }

  private Expr yieldExpression() {
    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }
    return new Expr.Yield(keyword, value);
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

  private boolean checkPrevious(TokenType type) {
    if (isAtEnd()) return false;
    return previous().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() { return peek().type == EOF; }

  private Token peek() { return tokens.get(current); }

  private Token peekNext() {
    if (current + 1 >= tokens.size()) return tokens.get(tokens.size() - 1); // EOF
    return tokens.get(current + 1);
  }

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

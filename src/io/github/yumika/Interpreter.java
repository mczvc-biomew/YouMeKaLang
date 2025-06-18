package io.github.yumika;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class Interpreter implements
    Expr.Visitor<Object>,
    Stmt.Visitor<Void>
{
  final Environment globals;
  private Environment environment;

  private final Map<Expr, Integer> locals = new HashMap<>();

  Interpreter() {
    globals = new Environment();
    environment = globals;

    initGlobalDefinitions(globals);
  }

  Interpreter(Environment env) {
    this.globals = env;
    environment = globals;

    initGlobalDefinitions(globals);
  }

  void initGlobalDefinitions(Environment globalEnv) {
    globalEnv.define("undefined", new YmkUndefined());
    globalEnv.define("env", new YmkEnv());
    globalEnv.define("str", new YmkCallable() {
      @Override
      public int arity() {
        return -2;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        StringBuilder strBuilder = new StringBuilder();
        for (Object argument : arguments) {
          strBuilder.append(argument.toString()).append(" ");
        }
        return strBuilder.toString();
      }
    });
    globalEnv.define("clock", new YmkCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });

    globalEnv.define("exit", new YmkCallable() {
      @Override
      public int arity() { return 0;}

      @Override
      public Void call(Interpreter interpreter,
                       List<Object> arguments) {
        System.exit(0);
        return null;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });
  }

  Environment getEnvironment() {
    return environment;
  }

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      YouMeKa.runtimeError(error);
    }
  }

  private Object evaluate(Expr expr) { return expr.accept(this); }

  // Statements and State execute
  private void execute(Stmt stmt) { stmt.accept(this); }

  // Resolving and Binding resolve
  void resolve(Expr expr, int depth) { locals.put(expr, depth); }
  boolean resolveLogical(Expr expr, int depth) {
    if (expr instanceof Expr.Variable) {
      resolve(expr, depth);
      return true;
    }
    if (expr instanceof Expr.Binary) {
      boolean leftResult = resolveLogical(((Expr.Binary)expr).left, depth);
      boolean rightReult = resolveLogical(((Expr.Binary)expr).right, depth);
      return leftResult || rightReult;
    } else if (expr instanceof Expr.Unary) {
      return resolveLogical(((Expr.Unary) expr).right, depth);
    }

    return false;
  }

  void executeBlock(List<Stmt> statements,
                   Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  Object evaluateExpr(Expr expr, Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;
      return evaluate(expr);
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitCaseStmt(Stmt.Case stmt) {
    Object value = evaluate(stmt.expression);

    for (Stmt.Case.WhenClause clause : stmt.whenClauses) {
      Object match = evaluate(clause.match);
      if (isEqual(value, match)) {
        execute(clause.body);
        return null;
      }
    }

    if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    Object superclass = null;
    if (stmt.superclass != null) {
      superclass = evaluate(stmt.superclass);
      if (!(superclass instanceof YmkClass)) {
        throw new RuntimeError(stmt.superclass.name,
            "Superclass must be a class.");
      }
    }

    // Inheritance interpret-superclass
    environment.define(stmt.name.lexeme, null);

    // Inheritance begin-superclass-environment
    if (stmt.superclass != null) {
      environment = new Environment(environment);
      environment.define("super", superclass);
    }

    // interpret-methods
    Map<String, YmkFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      // interpreter-method-initializer
      YmkFunction function = new YmkFunction(method, environment,
          method.name.lexeme.equals("init"));

      methods.put(method.name.lexeme, function);
    }

    // Inheritance interpreter-construct-class
    YmkClass klass = new YmkClass(stmt.name.lexeme,
        (YmkClass)superclass, methods);

    if (superclass != null) {
      environment = environment.enclosing;
    }

    environment.assign(stmt.name, klass);
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    YmkFunction function = new YmkFunction(stmt, environment,
        false);

    // Classes construct-function
    environment.define(stmt.name.lexeme, function);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitImportStmt(Stmt.Import stmt) {
    StringBuilder pathBuilder = new StringBuilder();
    String aliasName = stmt.alias.lexeme;

    for (int i = 0; i < stmt.pathParts.size(); i++) {
      pathBuilder.append(stmt.pathParts.get(i).lexeme);
      if (i < stmt.pathParts.size() - 1) {
        pathBuilder.append("/");
      }
    }

    String path = pathBuilder.toString();

    String sourcePath = System.getProperty("user.dir") + '/' + resolveModulePath(path);

    String source;
    try {
      source = Files.readString(Paths.get(sourcePath));
    } catch (IOException e) {
      throw new RuntimeError(null,
          "Cannot read module '" + sourcePath + "'");
    }

    List<Stmt> statements = new Parser(new Scanner(source).scanTokens()).parse();

    Environment moduleEnv = new Environment(); // isolated;

    Interpreter moduleInterpreter = new Interpreter(moduleEnv);
    for (Stmt s : statements) {
      moduleInterpreter.interpret(Collections.singletonList(s));
    }

    YmkClass moduleKlass = new YmkClass("Module", null, new HashMap<>());
    YmkInstance namespace = new YmkInstance(moduleKlass);
    moduleEnv.forEach((k, v) -> namespace.set(k, v));
    environment.define(aliasName, namespace);

    return null;
  }

  private String resolveModulePath(String importPath) {
    // Normalize to use forward slashes
    String normalized = importPath.replace(".", "/");

    // Add your supported extensions in order of priority
    String[] extensions = { ".ymk", ".yumika", ".mika" };

    for (String ext : extensions) {
      File candidate = new File(normalized + ext);
      if (candidate.exists()) {
        return candidate.getPath();
      }
    }

    throw new RuntimeError(null, "Module '" + importPath + "' not found.");
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitPutsStmt(Stmt.Puts stmt) {
    Object value = evaluate(stmt.expression);
    System.out.print(stringify(value));
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
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
  public Void visitWhileStmt(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.body);
    }

    return null;
  }

  @Override
  public Object visitArrayExpr(Expr.ArrayLiteral expr) {
    List<Object> elements = new ArrayList<>();
    for (Expr element: expr.elements) {
      elements.add(evaluate(element));
    }
    return elements;
  }

  @Override
  public Object visitArrayAssignExpr(Expr.ArrayAssign expr) {
    Object arrayOrMapOrObjInstance = evaluate(expr.array);
    Object index = evaluate(expr.index);
    Object value = evaluate(expr.value);

    if (!(arrayOrMapOrObjInstance instanceof List || arrayOrMapOrObjInstance instanceof Map || arrayOrMapOrObjInstance instanceof YmkInstance)) {
      throw new RuntimeError(null, "Can only index arrays or object literals.");
    } else if (arrayOrMapOrObjInstance instanceof List) {
      if (!(index instanceof Double)) {
        throw new RuntimeError(null, "Index must be a number.");
      }
    } else if (arrayOrMapOrObjInstance instanceof Map) {
      if (!(index instanceof String)) {
        throw new RuntimeError(null, "Key must be a valid.");
      }
    }
    if (!(value instanceof Double || value instanceof String || value instanceof List || value instanceof Map)) {
      throw new RuntimeError(expr.name, "Value must be a number, string, or array.");
    }

    if (arrayOrMapOrObjInstance instanceof List<?>) {
      int i = ((Double) index).intValue();
      List<Object> list = (List<Object>) arrayOrMapOrObjInstance;
      list.set(i, value);

      return value;
    } else if (arrayOrMapOrObjInstance instanceof Map<?, ?>) {
      String key = ((String) index);
      Map<String, Object> map = (Map<String, Object>) arrayOrMapOrObjInstance;
      map.put(key, value);

      return value;
    } else if (arrayOrMapOrObjInstance instanceof YmkInstance) {
      String key = ((String) index);
      YmkInstance instance = (YmkInstance) arrayOrMapOrObjInstance;
      instance.set(key, value);

      return value;
    }

    throw new RuntimeError(expr.name, "Cannot assign an array element to non-array.");
  }

  @Override
  public Object visitArrayIndexExpr(Expr.ArrayIndex expr) {
    Object arrayOrMapOrObjInstance = evaluate(expr.array);
    Object index = evaluate(expr.index);

    if (!(arrayOrMapOrObjInstance instanceof List || arrayOrMapOrObjInstance instanceof Map || arrayOrMapOrObjInstance instanceof YmkInstance)) {
      throw new RuntimeError(expr.bracket, "Can only index arrays or objects.");
    } else if (arrayOrMapOrObjInstance instanceof List) {
      if (!(index instanceof Double)) {
        throw new RuntimeError(expr.bracket, "Index must be a number.");
      }
    } else if (arrayOrMapOrObjInstance instanceof Map) {
      if (!(index instanceof String)) {
        throw new RuntimeError(expr.bracket, "Key must be a valid.");
      }
    }

    if (arrayOrMapOrObjInstance instanceof List) {
      List<?> list = (List<?>) arrayOrMapOrObjInstance;
      int i = ((Double) index).intValue();
      if (i < 0 || i >= list.size()) {
        throw new RuntimeError(expr.bracket, "Array index out of bounds.");
      }

      return list.get(i);
    } else if (arrayOrMapOrObjInstance instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) arrayOrMapOrObjInstance;
      String key = ((String) index);
      if (!(map.containsKey(key))) {
        throw new RuntimeError(expr.bracket, "Object doesn't contain key: " + key);
      }

      return map.get(key);
    } else if (arrayOrMapOrObjInstance instanceof YmkInstance) {
      YmkInstance instance = (YmkInstance) arrayOrMapOrObjInstance;
      String key = ((String) index);
      if (!instance.containsField(key)) {
        throw new RuntimeError(expr.bracket,
            "Object doesn't contain field: " + key);
      }
      return instance.get(key);
    }
    throw new RuntimeError(expr.bracket, "Cannot recognize object type.");
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    // Resolving and Binding resolved-assign
    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, expr.name, value);
    } else {
      globals.assign(expr.name, value);
    }

    return value;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      // binary-equality
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);
      // binary-comparison
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double)left > (double)right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left >= (double)right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left < (double)right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left <= (double)right;
      case MINUS:
        // check-minus-operand
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return (String)left + (String)right;
        }

        if (expr.left instanceof Expr.Variable && expr.right instanceof Expr.Variable) {
          Object leftVar = evaluate((Expr)expr.left);
          Object rightVar = evaluate((Expr)expr.right);
          System.out.println(leftVar + " " + rightVar);
          if (leftVar instanceof String && rightVar instanceof String) {
            return (String)leftVar + (String)rightVar;
          }
        }

        // string-number-wrong-type
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");

      case SLASH:
        // check-slash-operand
        checkNumberOperands(expr.operator, left, right);
        return (double)left / (double)right;
      case STAR:
        // check-star-operand
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitBlockExpr(Expr.Block expr) {
    try {
      executeBlock(expr.statements, new Environment(environment));
    } catch (Return value) {
      return value.value;
    }
    return globals.getAt(0, "undefined");
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) {
      arguments.add(evaluate(argument));
    }

    // check-is-callable
    if (!(callee instanceof YmkCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    YmkCallable function = (YmkCallable)callee;
    // check-arity
    if (function.arity() != -2 && arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }

    return function.call(this, arguments);
  }

  @Override
  public Object visitCaseExpr(Expr.Case expr) {
    Object value = evaluate(expr.expression);

    for (Expr.Case.WhenClause clause : expr.whenClauses) {
      Object match = evaluate(clause.match);
      if (isEqual(value, match)) {
        return evaluate(clause.result);
      }
    }

    if (expr.elseBranch != null) {
      return evaluate(expr.elseBranch);
    }

    return null;
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    if (expr.object instanceof Expr.This) {
      resolve(expr.object, 0);
    }

    Object object = evaluate(expr.object);
    if (object instanceof YmkInstance) {
      return ((YmkInstance) object).get(expr.name);
    }

    if (object instanceof Map) {
      Object getValue = ((Map<String, Object>)object).get(expr.name.lexeme);
      return  getValue;
    }

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) { return evaluate(expr.expression); }

  @Override
  public Object visitLambdaExpr(Expr.Lambda expr) {
    Object thisContext = null;
    if (environment.contains("this")) {
      thisContext = environment.getAt(0, "this");
    }
    resolveLogical(expr.body, 1);
    return new YmkLambda(expr, environment, thisContext);
  }

  @Override
  public Object visitListComprehensionExpr(Expr.ListComprehension expr) {
    Object iterable = evaluate(expr.iterable);
    if (!(iterable instanceof List)) {
      throw new RuntimeError(expr.variable,
          "Expected iterable in list comprehension.");
    }
    List<Object> result = new ArrayList<>();
    List<?> source = (List<?>) iterable;
    for (Object item : source) {
      Environment loopEnv = new Environment(environment);
      // scoped loop
      loopEnv.define(expr.variable.lexeme, item);
      if (expr.condition != null) {

        if (!resolveLogical(expr.condition, 0)) {
          throw new RuntimeError(expr.variable,
              "Currently supports binary and unary expressions.");
        }
        Object cond = evaluateWithEnv(expr.condition, loopEnv);
        if (!(cond instanceof Double) && (!(cond instanceof Boolean) || !(Boolean) cond))
          continue;
      }
      resolve(expr.elementExpr, 0);
      Object value = evaluateWithEnv(expr.elementExpr, loopEnv);
      result.add(value);
    }
    return result;
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) { return expr.value; }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
    YmkClass objectLiteralKlass = new YmkClass("Object", null, new HashMap<>());
    YmkInstance self = new YmkInstance(objectLiteralKlass);

    for (Map.Entry<String, Expr> entry : expr.properties.entrySet()) {
      Object value = evaluate(entry.getValue());
      if (value instanceof YmkLambda lambda) {
        lambda = lambda.bind(self);
        self.set(entry.getKey(), lambda);
      } else {
        self.set(entry.getKey(), value);
      }
    }
    return self;
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof YmkInstance)) {
      throw new RuntimeError(expr.name,
          "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((YmkInstance)object).set(expr.name, value);
    return value;
  }

  @Override
  public Object visitSuperExpr(Expr.Super expr) {
    int distance = locals.get(expr);
    YmkClass superclass = (YmkClass)environment.getAt(
        distance, "super");

    YmkInstance object = (YmkInstance)environment.getAt(
        distance -  1, "this");

    YmkFunction method = superclass.findMethod(expr.method.lexeme);

    if (method == null) {
      throw new RuntimeError(expr.method,
          "Undefined property '" + expr.method.lexeme + "'.");
    }

    return method.bind(object);
  }

  @Override
  public Object visitThisExpr(Expr.This expr) { return lookUpVariable(expr.keyword, expr); }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case NOT:
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
    }

    return null;
  }

  @Override
  public Object visitUndefinedExpr(Expr.Undefined expr) {
    return globals.getAt(0, "undefined");
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    // Resolving and Binding call-look-up-variable
    return lookUpVariable(expr.name, expr);
  }

  private Object evaluateWithEnv(Expr expr, Environment env) {
    Environment previous = this.environment;
    try {
      this.environment = env;
      return evaluate(expr);
    } finally {
      this.environment = previous;
    }
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      Object value = environment.getAt(distance, name.lexeme);
      return value;
    } else {
      try {
        return globals.get(name);
      } catch (RuntimeError.UndefinedException undefEx) {
        throw new RuntimeError.ReferenceError(name,
            "Uncaught ReferenceError: " + name.lexeme + " is not defined");
      }
    }
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private String stringify(Object object) {
    if (object == null) return "null";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }

      return text;
    }

    return object.toString();
  }


}

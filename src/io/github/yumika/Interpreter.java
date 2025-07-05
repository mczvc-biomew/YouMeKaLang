package io.github.yumika;

import io.github.yumika.javainterop.*;
import io.github.yumika.modules.YmkMath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Interpreter implements
    Expr.Visitor<Object>,
    Stmt.Visitor<Void>
{
  final Environment globals;
  Environment environment;

  private final Map<Expr, Integer> locals = new HashMap<>();

  private List<Stmt> pausedStatements = null;
  private int pausedIndex = 0;
  private boolean paused = false;


  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
      runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
      });
  private final Map<Integer, ScheduledFuture<?>> timers = new HashMap<>();
  private int timerIdCounter = 0;
  AtomicInteger runningTimers = new AtomicInteger();

  Interpreter() {
    globals = new Environment();
    environment = globals;

  }

  Interpreter(Environment env) {
    this.globals = env;
    environment = globals;
  }

  void initJavaPackage(Environment globals) {
    globals.define("java", new JavaPackage("java"));
    globals.define("Math", new YmkMath(this));
  }

  void initGlobalDefinitions(Environment globalEnv) {
    globalEnv.define("undefined", YmkUndefined.INSTANCE);
    globalEnv.define("__types__", new YmkInstance(new YmkClass("__types__", null, null)));
    globalEnv.define("__builtins__", Builtins.loadBuiltins(this));
  }

  public int setTimeout(YmkCallable fn, double delayMs) {
    int id = timerIdCounter++;
    runningTimers.incrementAndGet();
    ScheduledFuture<?> future = scheduler.schedule(() -> {
      try {
        fn.call(this, List.of(), Map.of());
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        runningTimers.decrementAndGet();
      }
    }, (long) delayMs, TimeUnit.MILLISECONDS);
    timers.put(id, future);
    return id;
  }


  public int setInterval(YmkCallable fn, double intervalMs) {
    int id = timerIdCounter++;
    final int[] counter = {0};
//    runningTimers.incrementAndGet();
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
      try {
        List<Object> args = fn.arity() >= 1 ? List.of((double) counter[0]++) : List.of();
        fn.call(this, args,  Map.of());
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
//        runningTimers.decrementAndGet();
      }
    }, (long) intervalMs, (long) intervalMs, TimeUnit.MILLISECONDS);
    timers.put(id, future);
    return id;
  }

  public void clearTimer(int id) {
    ScheduledFuture<?> task = timers.get(id);
    if (task != null) {
      task.cancel(true);     // stop the scheduled task
      timers.remove(id);     // remove it from the registry
    }
  }

  public void shutdownScheduler() {
    scheduler.shutdownNow();
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
  void execute(Stmt stmt) {
    stmt.accept(this);
  }

  // Resolving and Binding resolve
  void resolve(Expr expr, int depth) { locals.put(expr, depth); }
  boolean resolveStmt(Stmt stmt, int depth) {
    if (stmt instanceof Stmt.Return returnStmt) {
      return resolveLogical(returnStmt.value,  depth);
    } else if (stmt instanceof Stmt.Expression expressionStmt) {
      return resolveLogical(expressionStmt.expression, depth);
    }
    return false;
  }
  boolean resolveLogical(Expr expr, int depth) {
    if (expr instanceof Expr.Variable || expr instanceof Expr.This) {
      resolve(expr, depth);
      return true;
    }

    if (expr instanceof Expr.Binary) {
      boolean leftResult = resolveLogical(((Expr.Binary)expr).left, depth);
      boolean rightReult = resolveLogical(((Expr.Binary)expr).right, depth);
      return leftResult || rightReult;

    } else if (expr instanceof Expr.Unary) {
      return resolveLogical(((Expr.Unary) expr).right, depth);

    } else if (expr instanceof Expr.Function function) {
      boolean funcResult = true;
      for (Stmt funcStmts : function.body) {
        funcResult = resolveStmt(funcStmts, depth);
      }
      return funcResult;

    } else if (expr instanceof Expr.Get getExpr) {
      boolean getResult = true;
      getResult |= resolveLogical(getExpr.object, depth);
      return getResult;

    } else if (expr instanceof Expr.Set setExpr) {
      boolean setResult = true;
      setResult |= resolveLogical(setExpr.object, depth);
      setResult |= resolveLogical(setExpr.value, depth);
      return setResult;
    } else if (expr instanceof Expr.Yield yieldExpr) {
      return resolveLogical(yieldExpr.value, depth);
    }

    return false;
  }

  void executeBlock(List<Stmt> statements,
                   Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      int i = paused && statements == pausedStatements ? pausedIndex : 0;
      for (; i < statements.size(); i++) {
        try {
          execute(statements.get(i));
        } catch (GeneratorInterpreter.YieldException yield) {
          pausedStatements = statements;
          pausedIndex = i + 1;
          paused = true;
          throw yield;
        }
      }

      // Finished execution normally
      paused = false;
      pausedStatements = null;

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

  public void resumeExecution() {
    if (!paused || pausedStatements == null) return;

    try {
      for (int i = pausedIndex; i < pausedStatements.size(); i++) {
        try {
          execute(pausedStatements.get(i));
        } catch (GeneratorInterpreter.YieldException yield) {
          pausedIndex = i + 1;
          paused = true;
          throw yield;
        }
      }

      // Done executing
      paused = false;
      pausedStatements = null;
    } catch (GeneratorInterpreter.YieldException yield) {
      throw yield;
    }
  }

  YmkCallable isTypeOf(Class<?> cls) {
    return new YmkCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> args, Map<String, Object> kwargs) {
        return cls.isInstance(args.get(0));
      }

      @Override
      public String toString() {
        return "<native fn is" + cls.getSimpleName() + ">";
      }
    };
  }

  YmkCallable isTypeOfFunction() {
    return new YmkCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> args,
                         Map<String, Object> kwargs) {
        return args.get(0) instanceof YmkCallable || args.get(0) instanceof YmkFunction;
      }

      @Override
      public String toString() {
        return "<native fn is Function>";
      }
    };
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    if (paused && pausedStatements == stmt.statements) {
      executeBlock(stmt.statements, this.environment);
    } else {
      executeBlock(stmt.statements, new Environment(environment));
    }
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

    for (Token interfaceName : stmt.interfaces) {
      Object interfaceObj = environment.get(interfaceName);

      if (!(interfaceObj instanceof Stmt.Interface iface)) {
        throw new RuntimeError(interfaceName,
            "Unknown interface: " + interfaceName.lexeme);
      }

      for (Stmt.Function method : iface.methods) {
        if (!stmt.methods.containsKey(method.name.lexeme)) {
          throw new RuntimeError(stmt.name,
              "Class '" + stmt.name.lexeme + "' does not implement method '"
                  + method.name.lexeme + "' from interface '" + interfaceName.lexeme + "'");
        }
      }
    }

    // Inheritance interpret-superclass
    environment.define(stmt.name.lexeme, null);

    // Inheritance begin-superclass-environment
    if (stmt.superclass != null) {
      environment = new Environment(environment);
      environment.define("super", superclass);
    }

    Map<String, YmkFunction> methods = new HashMap<>();
    for (Map.Entry<String, Stmt.Function> method : stmt.methods.entrySet()) {
      YmkFunction function = new YmkFunction(method.getValue(), environment,
          method.getKey().equals("init"));

      methods.put(method.getValue().name.lexeme, function);
    }

    YmkClass klass = new YmkClass(stmt.name.lexeme,
        (YmkClass)superclass, methods);

    if (superclass != null) {
      environment = environment.enclosing;
    }

    environment.assign(stmt.name, klass);
    return null;
  }

  @Override
  public Void visitDestructuringVarStmt(Stmt.DestructuringVarStmt stmt) {
    Object object = evaluate(stmt.initializer);
    if (!(object instanceof Map || object instanceof YmkInstance)) {
      throw new RuntimeError(null, "Destructuring requires and object.");
    }

    for (Stmt.DestructuringVarStmt.DestructuringField field : stmt.fields) {
      Object value = null;
      String key = field.name.lexeme;

      if (object instanceof Map) {
        value = ((Map<?, ?>) object).getOrDefault(key, null);

      } else if (object instanceof YmkInstance) {
        try {
          value = ((YmkInstance) object).get(new Token(TokenType.IDENTIFIER, key, null, 0),
              this);
        } catch (RuntimeError re) {
          value = null;
        }
      }

      // Apply default value if undefined
      if (value == null && field.defaultValue != null) {
        value = evaluate(field.defaultValue);
      }

      environment.define(key, value);
    }

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    // Check if functon contains a 'yield', indicating it's a generator
    boolean isGenerator = isGeneratorFunction(stmt);
    YmkCallable callable;
    if (isGenerator) {
      callable = new YmkGenerator(stmt, environment);
    } else {
      YmkFunction function = new YmkFunction(stmt, environment,
          false);
      Object decorated = function;

      for (int i = stmt.decorators.size() - 1; i >= 0; i--) {
        resolveLogical(stmt.decorators.get(i), 0);
        Object deco = evaluate(stmt.decorators.get(i));

        if (!(deco instanceof YmkCallable)) {
          throw new RuntimeError(null, "Decorator must be callable.");
        }
        decorated = ((YmkCallable) deco).call(this, List.of(decorated), Map.of());
      }
      callable = (YmkCallable) decorated;
    }

    // Classes construct-function
    environment.define(stmt.name.lexeme, callable);
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
    String alias = stmt.alias != null ? stmt.alias.lexeme : stmt.path.lexeme;

    if (environment.exists(alias)) {
      System.err.println("Warning: '"  + alias + "' already imported." + stmt.alias.line);
      return null;
    }

    if (stmt.path.lexeme.startsWith("java.")) {
      return null;
    }
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
    moduleEnv.forEach((k, v) -> namespace.set(k, v, this));
    environment.define(aliasName, namespace);

    return null;
  }

  @Override
  public Void visitInterfaceStmt(Stmt.Interface stmt) {
    environment.define(stmt.name.lexeme, stmt);
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
    resolve(stmt.expression, 0);
    Object value = evaluate(stmt.expression);
    print(value, "\n");
    return null;
  }

  @Override
  public Void visitPutsStmt(Stmt.Puts stmt) {
    resolve(stmt.expression, 0);
    Object value = evaluate(stmt.expression);
    print(value);
    return null;
  }

  private void print(Object ...args) {
    if (args.length == 1) {
      System.out.print(CUtils.stringify(args[0], 0));
    } else {
      System.out.print(CUtils.stringify(0, args));
    }
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
  }

  @Override
  public Void visitThrowStmt(Stmt.Throw stmt) {
    Object value = evaluate(stmt.error);
    throw new RuntimeError(null, value.toString());
  }

  @Override
  public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
    try {
      executeBlock(stmt.tryBlock, new Environment(environment));
    } catch (RuntimeError err) {
      Environment catchEnv = new Environment(environment);
      catchEnv.define(stmt.errorVar.lexeme, err.getMessage());
      executeBlock(stmt.catchBlock, catchEnv);
    }
    return null;
  }

  @Override
  public Void visitTypeDefStmt(Stmt.TypeDef stmt) {
    YmkInstance userTypes = (YmkInstance) globals.get("__types__");
    userTypes.getFields().put(stmt.name.lexeme, stmt.definition);

    environment.define(stmt.name.lexeme, stmt.definition);
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);

      if (stmt.type != null) {
        Object typeDef = environment.get(stmt.type.lexeme);
        if (!isTypeMatchAgainstDef(value, typeDef)) {
          throw new RuntimeError(stmt.name, "Type error: expected '" + stmt.type.lexeme + "'");
        }
      }
    }
    resolveLogical(stmt.initializer, 0);
    environment.define(stmt.name.lexeme, value);
    return null;
  }

  /**
   * Checks the type of Map or YmkInstance (object literal)
   * by checking its shape if it fits the object literal.
   * @param value The value to type check;
   * @param typeDef Type definition to check;
   * @return <b>true</b>, if it has or contains the key; <b>false</b> otherwise;
   */
  protected boolean isTypeMatchAgainstDef(Object value, Object typeDef) {
    if (typeDef instanceof Expr.ObjectLiteral defStruct) {
      if (!(value instanceof Map<?, ?> || value instanceof YmkInstance
          || value instanceof Expr.ObjectLiteral)) return false;
      if (value instanceof Expr.ObjectLiteral objLiteral) {
//        if (!(objLiteral.properties instanceof Expr.ObjectLiteral.Pair)) return false;
        var typeA = new ArrayList<String>();
        objLiteral.properties.forEach(a -> {
          typeA.add( a instanceof Expr.ObjectLiteral.Pair pairA ? pairA.key.lexeme : null );
        } );
        var typeB = new ArrayList<String>();
        defStruct.properties.forEach(b -> {
          typeB.add( b instanceof Expr.ObjectLiteral.Pair pairB ? pairB.key.lexeme : null );
        });

        boolean all = typeB.containsAll(typeA);
        return all;
      }
      for (Expr.ObjectLiteral.Property prop : defStruct.properties) {
        if (prop instanceof Expr.ObjectLiteral.Pair pair) {
          String key = pair.key.lexeme;

          if (value instanceof Map<?, ?> valMap) {
            if (!valMap.containsKey(key)) return false;

          } else if (value instanceof YmkInstance inst) {
            if (!inst.getFields().containsKey(key)) return false;
          }
          // Optional: resolve expected type via prop.value and check `typeof(value.get(key))`
        }
      }

      return true;
    }

    return true; // fallback: not enforced when type-def is not an object literal
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    try {
      while (isTruthy(evaluate(stmt.condition))) {
        try {
          execute(stmt.body);
        } catch (GeneratorInterpreter.YieldException yield) {
          throw yield;
        }
      }
    } catch (GeneratorInterpreter.YieldException yield) {
      throw yield;
    }

    return null;
  }

  @Override
  public Object visitListLiteralExpr(Expr.ListLiteral expr) {
    List<Object> result = new ArrayList<>();

    if (expr.elements == null) return result;
    for (Expr element: expr.elements) {
      if (element instanceof Expr.Spread spread) {
        Object spreadValue = evaluate(spread);
        if (spreadValue instanceof List<?> list) {
          result.addAll(list);
        } else {
          throw new RuntimeError(null,
              "Spread target must be a list.");
        }
      } else {
        result.add(evaluate(element));
      }
    }
    return result;
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
    } else if (arrayOrMapOrObjInstance instanceof YmkInstance instance) {
      String key = index.toString();
      instance.set(key, value, this);

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
    } else if (arrayOrMapOrObjInstance instanceof YmkInstance instance) {
//      YmkInstance instance = (YmkInstance) arrayOrMapOrObjInstance;
      String key = index.toString();
      if (!instance.containsField(key)) {
        throw new RuntimeError(expr.bracket,
            "Object doesn't contain field: " + key);
      }
      return instance.get(key, this);
    }
    throw new RuntimeError(expr.bracket, "Cannot recognize object type.");
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    // reassigning designated name at local (and enclosing) distance with value
    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, expr.name, value);
    } else {
      try {
        globals.assign(expr.name, value);
      } catch (RuntimeError.UndefinedException e) {
        environment.assign(expr.name, value);
      }
    }

    return value;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    String op = expr.operator.lexeme;

    String method = switch (expr.operator.type) {
      case PLUS -> "__add__";
      case MINUS -> "__sub__";
      case STAR -> "__mul__";
      case SLASH -> "__div__";
      case PERCENT -> "__mod__";
      case EQUAL_EQUAL -> "__eq__";
      case BANG_EQUAL -> "__ne__";
      case GREATER -> "__gt__";
      case GREATER_EQUAL -> "__ge__";
      case LESS -> "__lt__";
      case LESS_EQUAL -> "__le__";
      default -> null;
    };

    if (method != null && left instanceof YmkInstance) {
      Object overload = ((YmkInstance) left).getOverload(method, this);
      if (overload instanceof YmkCallable fn) {
        return fn.call(this, List.of(right), null);
      }
    }

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

        if (left instanceof String && right instanceof Double) {
          return (String)left + right.toString();
        }

        if (right instanceof String && left instanceof Double) {
          return left.toString() + (String)right;
        }

        if (expr.left instanceof Expr.Variable && expr.right instanceof Expr.Variable) {
          Object leftVar = evaluate((Expr)expr.left);
          Object rightVar = evaluate((Expr)expr.right);
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
        if (left instanceof Double && right instanceof Double) {
          return (double)left * (double)right;
        }
        if (left instanceof String && right instanceof Double) {
          return CUtils.repeatString((String) left, (int) ((double) right));
        }
        if (left instanceof Double  && right instanceof String) {
          return CUtils.repeatString((String) right, (int) ((double) left));
        }
        throw new RuntimeError(expr.operator, "Operands must be two numbers or string * number.");
      case IN:
        if (right instanceof Map<?, ?> map) {
          return map.containsKey(left);
        }
        if (right instanceof List<?> list) {
          return list.contains(left);
        }
        if (right instanceof String str && left instanceof String sub) {
          return str.contains(sub);
        }
        if (right instanceof YmkInstance inst) {
          if (!(left instanceof String || left instanceof Double)) {
            throw new RuntimeError(expr.operator, "Left operand of 'in' must be a string when checking object keys.");
          }
          String key = left.toString();

          // Check field or method existence
          if (inst.getFields().containsKey(key)) return true;
          if (inst.getKlass().findMethod(key) != null) return true;
          return false;
        }
        throw new RuntimeError(expr.operator, "'in' requires a list, map, or string.");
    }

    throw new RuntimeError(expr.operator,
        "Operator '" + op + "' not supported for " + CUtils.stringify(left, 0));

    // Unreachable.
//    return null;
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

    List<Object> args = new ArrayList<>();
    for (Expr argExpr : expr.positionalArgs) {
      args.add(evaluate(argExpr));
    }

    Map<String, Object> kwargs = new HashMap<>();
    for (Map.Entry<String, Expr> entry : expr.keywordArgs.entrySet()) {
      kwargs.put(entry.getKey(), evaluate(entry.getValue()));
    }

    if (callee instanceof JavaClassWrapper) {
      return ((JavaClassWrapper) callee).call(args);
    }
    if (callee instanceof JavaInstanceMethod) {
      return ((JavaInstanceMethod) callee).call(args);
    }
    if (callee instanceof JavaStaticMethod) {
      return ((JavaStaticMethod) callee).call(args);
    }

    // check-is-callable
    if (!(callee instanceof YmkCallable function)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    // check-arity
    if (function.arity() > -1 && (args.size() != function.arity())) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          args.size() + ".");
    }

    return function.call(this, args, kwargs);
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
  public Object visitCompoundAssignExpr(Expr.CompoundAssign expr) {
    Object oldValue = environment.get(expr.name);
    Object right = evaluate(expr.value);

    if (!(oldValue instanceof Double) || !(right instanceof Double)) {
      throw new RuntimeError(expr.operator, "Operands must be numbers.");
    }

    double left = (Double) oldValue;
    double rightValue = (Double) right;
    double result;

    switch (expr.operator.type) {
      case PLUS_EQUAL -> result = left + rightValue;
      case MINUS_EQUAL -> result = left - rightValue;
      default -> throw new RuntimeError(expr.operator, "Unknown compound assignment.");
    }

    environment.assign(expr.name, result);
    return result;
  }

  @Override
  public Object visitFunctionExpr(Expr.Function expr) {
    return new YmkFunction(expr, environment, false);
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    if (expr.object instanceof Expr.This) {
      resolve(expr.object, 1);
    }

    Object object = evaluate(expr.object);

    if ("__class__".equals(expr.name.lexeme)) {
      return getTypeName(object);
    }
    if (object instanceof JavaPackage) {
      return ((JavaPackage) object).get(expr.name.lexeme);
    }
    if (object instanceof JavaClassWrapper) {
      return ((JavaClassWrapper) object).get(expr.name.lexeme);
    }
    if (object instanceof JavaInstanceWrapper) {
      return ((JavaInstanceWrapper) object).get(expr.name.lexeme);
    }


    if (object instanceof YmkInstance) {
      return ((YmkInstance) object).get(expr.name, this);
    }

    if (object instanceof Map) {
      Object getValue = ((Map<String, Object>)object).get(expr.name.lexeme);
      return  getValue;
    }

    if (object instanceof YmkGenerator generator) {
      String prop = expr.name.lexeme;
      if ("next".equals(prop)) {
        return new YmkNativeFunction("next", 0, (interpreter, args) -> generator.next());
      }
    }

    if (object instanceof YmkGenerator.BoundGenerator bound) {
      if ("next".equals(expr.name.lexeme)) {
        return new YmkNativeFunction("next", 0, (interpreter, args) -> bound.next());
      }
    }

    if (object == null) return null;

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) { return evaluate(expr.expression); }

  @Override
  public Object visitInterpolatedStringExpr(Expr.InterpolatedString expr) {
    StringBuilder builder = new StringBuilder();
    for (Expr part : expr.parts) {
      Object value = evaluate(part);
      builder.append(value != null ? value.toString() : "null");
    }
    return builder.toString();
  }

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
  public Object visitMatchExpr(Expr.Match expr) {
    Object target = evaluate(expr.value);

    for (Expr.MatchCase kase : expr.cases) {
      Environment matchEnv = new Environment(environment);
      if (kase.isElse || matchPattern(target, kase.pattern, matchEnv)) {
        resolveLogical(kase.body, 0);
        return evaluateWithEnv(kase.body, matchEnv);
      }
    }

    return null;
  }

  private boolean matchPattern(Object target, Expr pattern, Environment matchEnv) {

    if (pattern instanceof Expr.Literal lit) {
      return Objects.equals(target, lit.value);
    }

    if (pattern instanceof Expr.Variable var) {
      matchEnv.define(var.name.lexeme, target);
      return true;
    }

    if (pattern instanceof Expr.ListLiteral listPattern) {
      if (!(target instanceof List<?> targetList)) return false;
      if (listPattern.elements.size() != targetList.size()) return false;

      for (int i = 0; i < listPattern.elements.size(); i++) {
        if (!matchPattern(targetList.get(i), listPattern.elements.get(i), matchEnv)) {
          return false;
        }
      }

      return true;
    }

    if (pattern instanceof Expr.ObjectLiteral objPattern) {
      if (!(target instanceof Map<?, ?> targetMap) && !(target instanceof YmkInstance)) return false;

      for (Expr.ObjectLiteral.Property prop : objPattern.properties) {
        if (prop instanceof Expr.ObjectLiteral.Pair pair) {
          String key = pair.key.lexeme;
          Object value = null;

          if (target instanceof Map<?, ?> map) {
            if (!map.containsKey(key)) return false;
            value = map.get(key);
          } else if (target instanceof YmkInstance inst) {
            try {
              value = inst.get(new Token(TokenType.IDENTIFIER, key, null, 0), this);
            } catch (RuntimeError e) {
              return false;
            }
          }

          if (!matchPattern(value, pair.value, matchEnv)) return false;
        }
      }

      return true;
    }

    Object patt = evaluate(pattern);

    if (patt instanceof YmkInstance pi && target instanceof YmkInstance ti) {
      return pi.equals(ti);
    }

    return Objects.equals(target, patt);
  }

  @Override
  public Object visitNewTypedArrayExpr(Expr.NewTypedArray expr) {
    Object sizeVal = evaluate(expr.size);

    if (!(sizeVal instanceof Double)) {
      throw new RuntimeError(expr.type, "Array size must be a number.");
    }

    int size = ((Double) sizeVal).intValue();
    if (size < 0) {
      throw new RuntimeError(expr.type, "Array size cannot be negative.");
    }

    String typeName = expr.type.lexeme;
    Object[] result = new Object[size];

    Object defaultValue = switch (typeName) {
      case "int", "number" -> 0.0;
      case "string" -> "";
      case "bool", "boolean" -> false;
      default -> null;
    };

    Arrays.fill(result, defaultValue);
    return Arrays.asList(result);
  }

  @Override
  public Object visitNullCoalesceExpr(Expr.NullCoalesce expr) {
    Object left = evaluate(expr.left);
    if (left == null || left == YmkUndefined.INSTANCE) {
      return evaluate(expr.right);
    }
    return left;
  }

  @Override
  public Object visitObjectLiteralExpr(Expr.ObjectLiteral expr) {
    YmkClass objectLiteralKlass = new YmkClass("Object", null, new HashMap<>());
    YmkInstance self = new YmkInstance(objectLiteralKlass);

    if (expr.properties == null) return self;

    for (Expr.ObjectLiteral.Property prop : expr.properties) {
      if (prop instanceof Expr.ObjectLiteral.Pair pair) {
        Object value = evaluate(pair.value);
        if (value instanceof YmkLambda lambda) {
          lambda = lambda.bind(self);
          self.set(pair.key, lambda, this);
        } else {
          self.set(pair.key, value, this);
        }
      } else if (prop instanceof Expr.ObjectLiteral.Spread spread) {
        resolveLogical(spread.expression, 0);
        Object spreadValue = evaluate(spread.expression);
        if (spreadValue instanceof Map<?, ?> map) {
          for (Map.Entry<?, ?> entry : map.entrySet()) {
            self.set(entry.getKey().toString(), entry.getValue(), this);
          }
        } else if (spreadValue instanceof YmkInstance instance) {
          self.putAll(instance.getFields());
        } else {
          throw new RuntimeError(
              spread.expression instanceof Expr.Variable v ? v.name : null,
              "Spread target must be an object or map.");
        }
      } else if (prop instanceof Expr.ObjectLiteral.Accessor accessor) {
        YmkFunction fn = new YmkFunction(accessor.function, environment, false);
        resolveLogical(accessor.function, 1);
        fn = fn.bind(self);
        if (accessor.kind.type == TokenType.GET) {
          self.defineGetter(accessor.name.lexeme, fn);
        } else {
          self.defineSetter(accessor.name.lexeme, fn);
        }
      }
    }
    return self;
  }

  @Override
  public Object visitOptionalGetExpr(Expr.OptionalGet expr) {
    Object object = evaluate(expr.object);
    if (object == null || object == YmkUndefined.INSTANCE) {
      return YmkUndefined.INSTANCE;
    }
    return getProperty(object, expr.name);
  }

  @Override
  public Object visitPostfixExpr(Expr.Postfix expr) {
    Object value = environment.get(expr.variable.name);
    if (!(value instanceof Double)) {
      throw new RuntimeError(expr.operator, "Operand must be number.");
    }

    double original = (Double) value;
    double updated = original;

    switch (expr.operator.type) {
      case PLUS_PLUS: updated = original + 1; break;
      case MINUS_MINUS: updated = original - 1; break;
      default:
        throw new RuntimeError(expr.operator,
            "Unknown postfix operator.");
    }
    environment.assign(expr.variable.name, updated);
    return original;
  }

  @Override
  public Object visitPrefixExpr(Expr.Prefix expr) {
    Object value = environment.get(expr.variable.name);
    if (!(value instanceof Double)) {
      throw new RuntimeError(expr.operator, "Operand must be a number.");
    }

    double current = (Double) value;
    double updated = current;

    switch (expr.operator.type) {
      case PLUS_PLUS: updated = current + 1; break;
      case MINUS_MINUS: updated = current - 1; break;
      default:
        throw new RuntimeError(expr.operator, "Unknown prefix operator.");
    }
    environment.assign(expr.variable.name, updated);
    return updated;
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof YmkInstance)) {
      throw new RuntimeError(expr.name,
          "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((YmkInstance)object).set(expr.name, value, this);
    return value;
  }

  @Override
  public Object visitSpreadExpr(Expr.Spread expr) {
    List<Object> result;

    resolveLogical(expr.expression, 0);
    Object spreadValue = evaluate(expr.expression);
    if (spreadValue instanceof List<?> list) {
      result = new ArrayList<>(list);
    } else {
      throw new RuntimeError(
          null, "Spread target must be a list.");
    }
    return result;
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
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
  }

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

  @Override
  public Object visitYieldExpr(Expr.Yield expr) {
    Object value = null;
    if (expr.value != null) {
      value = evaluate(expr.value);
    }
    resolveLogical(expr, 0);
    throw new GeneratorInterpreter.YieldException(value);
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
      int location = environment.containsAt(name.lexeme, 32);
      Object value = null;
      if (location > -1) {
        value = environment.getAt(location, name.lexeme);
      } else {
        value = environment.getAt(distance, name.lexeme);
      }
      return value;
    } else {
      try {
        return globals.get(name);
      } catch (RuntimeError.UndefinedException undefEx) {
        try {
          YmkInstance builtins = (YmkInstance) globals.get("__builtins__");
          if (builtins.containsField(name.lexeme)) {
            return builtins.get(name.lexeme, this);
          }
          throw new RuntimeError.UndefinedException(undefEx);
        } catch (RuntimeError.UndefinedException undefEx2) {
          Object value = YmkUndefined.INSTANCE;
          try {
            value = environment.get(name.lexeme);
          } catch (RuntimeError.UndefinedException undefEx3) {
            throw new RuntimeError.ReferenceError(name,
                "Uncaught ReferenceError: " + name.lexeme + " is not defined");
          }
          return value;
        }
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
    if (left instanceof Double && right instanceof String) return;
    if (left instanceof String && right instanceof Double) return;
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  String getTypeName(Object value) {
    if (value == null) return "null";
    if (value instanceof Double) return "Number";
    if (value instanceof String) return "String";
    if (value instanceof Boolean) return "Boolean";
    if (value instanceof List<?>) return "Array";
    if (value instanceof YmkLambda || value instanceof YmkFunction) return "Function";
    if (value instanceof Expr.ObjectLiteral && isTypeDef(value)) return "TypeDef";
    if (value instanceof YmkInstance) return "Object";

    return "Unknown";
  }

  public Object getProperty(Object object, Token property) {
    String name = property.lexeme;

    if (object instanceof YmkInstance instance) {
      return instance.get(property, this);
    }

    if (object instanceof JavaInstanceWrapper wrapper) {
      return wrapper.get(name);
    }

    if (object instanceof Map map) {
      return map.getOrDefault(name, YmkUndefined.INSTANCE);
    }

    if (object instanceof List<?> list) {
      try {
        int index = Integer.parseInt(name);
        return list.get(index);
      } catch (NumberFormatException | IndexOutOfBoundsException e) {
        return YmkUndefined.INSTANCE;
      }
    }

    throw new RuntimeError(property, "Cannot access property '" + name + "' on type: " + getTypeName(object));
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  /**
   * Checks if the value is of expected type.
   * @param value Value to check against;
   * @param expectedType Expected type of
   *                     (int, number, bool, string, function,
   *                     array/list, map/object; defaults to true);
   * @return <b>true</b>, if it matches the type; <b>false</b> otherwise.
   */
  boolean isTypeMatch(Object value, String expectedType) {
    switch (expectedType) {
      case "int": return value instanceof Integer;
      case "number": return value instanceof Double;
      case "bool": return value instanceof Boolean;
      case "string": return value instanceof String;
      case "function": return value instanceof YmkCallable;
      case "array":
      case "list": return value instanceof List;
      case "map":
      case "object": return value instanceof Map || value instanceof YmkInstance;
      default: return true;
    }
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private boolean isTypeDef(Object typeDef) {
    for ( Object value : ((YmkInstance)globals.get("__types__")).getFields().values() ) {
      boolean ret = isTypeMatchAgainstDef(typeDef, value);
      if (ret) return true;
    }
    return false;
  }

  boolean isGeneratorFunction(Stmt.Function function) {
    YmkGenerator.GeneratorDetector detector = new YmkGenerator.GeneratorDetector();
    try {
      for (Stmt stmt : function.body) {
        stmt.accept(detector);
      }
    } catch(YmkGenerator.GeneratorDetectedException e) {
      return true;
    }
    return false;
  }


}

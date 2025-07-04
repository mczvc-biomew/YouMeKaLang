package io.github.yumika;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YmkFunction implements YmkCallable {
  final String name;
  final List<Token> params;
  final List<Token> paramTypes;
  final Token returnType;
  final List<Stmt> body;
  final boolean hasVarArgs;
  final boolean hasVarKwargs;
  final Token varArgsName;
  final Token kwArgsName;
  private final Environment closure;

  private final boolean isInitializer;

  YmkFunction(Stmt.Function declaration, Environment closure,
              boolean isInitializer) {
    this.isInitializer = isInitializer;
    // closure-constructor
    this.closure = closure;
    this.name = declaration.name.lexeme;
    this.params = declaration.params;
    this.paramTypes = declaration.paramTypes;
    this.returnType = declaration.returnType;
    this.body = declaration.body;
    this.hasVarArgs = declaration.hasVarArgs;
    this.hasVarKwargs = declaration.hasVarKwargs;
    this.varArgsName = declaration.varArgsName;
    this.kwArgsName = declaration.kwArgsName;
  }

  YmkFunction(Expr.Function expression, Environment closure,  boolean isInitializer) {
    this.name = null;
    this.params = expression.params;
    this.paramTypes = expression.paramTypes;
    this.returnType = expression.returnType;
    this.body = expression.body;
    this.closure = closure;
    this.isInitializer = isInitializer;
    this.hasVarArgs = expression.hasVarArgs;
    this.hasVarKwargs = expression.hasVarKwargs;
    this.varArgsName = expression.varArgsName;
    this.kwArgsName = expression.kwArgsName;
  }

  YmkFunction bind(YmkInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);

    return new YmkFunction(this, environment);
  }

  private YmkFunction(YmkFunction original, Environment closure) {
    this.name = original.name;
    this.params = original.params;
    this.paramTypes = original.paramTypes;
    this.returnType = original.returnType;
    this.body = original.body;
    this.isInitializer = original.isInitializer;
    this.closure = closure;
    this.hasVarArgs = original.hasVarArgs;
    this.hasVarKwargs = original.hasVarKwargs;
    this.varArgsName = original.varArgsName;
    this.kwArgsName = original.kwArgsName;
  }

  @Override
  public String toString() { return "<fn " + this.name + ">"; }

  @Override
  public int arity() { return (hasVarArgs || hasVarKwargs) ? -1 : params.size(); }

  // function-call
  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments,
                     Map<String, Object> kwargs) {
    Environment closure = new Environment(this.closure);
    int expectedParamsCount = params.size();
    int given = arguments.size();
    int varStart = expectedParamsCount;

    List<Object> varArgs = new ArrayList<>();
    Map<String, Object> kwArgs = new HashMap<>();
    // standard args
    // Normal arguments
    for (int i = 0; i < expectedParamsCount; i++) {
      Token param = params.get(i);
      Token paramType = null;
      Object argValue = arguments.get(i);

      if (i < paramTypes.size()) {
        paramType = paramTypes.get(i);
      }

      closure.define(param.lexeme, arguments.get(i));

      if (paramType != null) {
        try {
          Object expectedType = closure.get(paramType.lexeme);
          // User defined type check
          if (!interpreter.isTypeMatchAgainstDef(argValue, expectedType)) {
            throw new RuntimeError(param,
                "TypeError: Expected argument of user defined type '"
                    + paramType.lexeme + " " + CUtils.stringify(
                        expectedType, 0) + "', got: " +
                    CUtils.stringify(argValue, 0));
          }
        } catch (RuntimeError.UndefinedException error) {
          String expectedType = paramType.lexeme;
          // primitive type check
          if (!interpreter.isTypeMatch(argValue, expectedType)) {
            throw new RuntimeError(param,
                "TypeError: Expected argument of type '" + expectedType + "', got '" +
                    interpreter.getTypeName(argValue) + "'");

          }
        }
      }

    }

    if (hasVarArgs && varArgsName == null) {
      throw new RuntimeError(null,
          "Must have var args name.");
    }
    if (hasVarKwargs && kwArgsName == null) {
      throw new RuntimeError(null,
          "Must have kwargs name.");
    }

//  *args
    if (hasVarArgs) {
      for (int i = varStart; i < given; i++) {
        varArgs.add(arguments.get(i));
      }

      closure.define(varArgsName.lexeme, varArgs);
    }

//  **kwargs
    if (hasVarKwargs) {
      for (Map.Entry<String, Object> kwarg : kwargs.entrySet()) {
        kwArgs.put(kwarg.getKey(), kwarg.getValue());
      }
      closure.define(kwArgsName.lexeme, kwArgs);
    }

    try {
      interpreter.executeBlock(body, closure);

    // catch-return
    } catch (Return returnValue) {
      // Class creation return 'this'
      if (isInitializer) return this.closure.getAt(0, "this");

      if (returnType != null) {
        String expected = returnType.lexeme;
        if (!interpreter.isTypeMatch(returnValue.value, expected)) {
          throw new RuntimeError(returnType,
              "TypeError: Expected return of type '" + expected + "', got '" +
                  interpreter.getTypeName(returnValue.value) + "'");
        }
      }

      return returnValue.value;
    }

    // Classes return-this
    if (isInitializer) return this.closure.getAt(0, "this");
    return null;
  }
}

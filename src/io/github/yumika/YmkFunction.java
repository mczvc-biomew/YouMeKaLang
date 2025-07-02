package io.github.yumika;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YmkFunction implements YmkCallable {
  private final String name;
  private final List<Token> params;
  private final List<Token> paramTypes;
  private final Token returnType;
  private final List<Stmt> body;
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
  public int arity() { return params.size(); }

  // function-call
  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Environment closure = new Environment(this.closure);
    int normalParamCount = params.size();
    // standard args
    // Normal arguments
    for (int i = 0; i < normalParamCount && i < arguments.size(); i++) {
      Token param = params.get(i);
      Token paramType = paramTypes.get(i);
      Object argValue = arguments.get(i);

      if (paramType != null) {
        try {
          Object expectedType = closure.get(paramType.lexeme);
          if (!interpreter.isTypeMatchAgainstDef(argValue, expectedType)) {
            throw new RuntimeError(param,
                "TypeError: Expected argument of type '"
                    + paramType.lexeme + interpreter.stringify(expectedType) + "', got: " +
                    interpreter.stringify(argValue));
          }
        } catch (RuntimeError.UndefinedException error) {
          String expected = paramType.lexeme;
          if (!interpreter.isTypeMatch(argValue, expected)) {
            throw new RuntimeError(param,
                "TypeError: Expected argument of type '" + expected + "', got '" +
                    interpreter.getTypeName(argValue) + "'");

          }
        }
      }

      closure.define(params.get(i).lexeme,
          arguments.get(i));
    }

    if (hasVarArgs && varArgsName == null) {
      throw new RuntimeError(null,
          "Must have var args name.");
    }
    if (hasVarKwargs && kwArgsName == null) {
      throw new RuntimeError(null,
          "Must have kwargs name.");
    }

    // *args
    if (hasVarArgs) {
      List<Object> varArgs = new ArrayList<>();
      for (int i = normalParamCount; i < arguments.size(); i++) {
        varArgs.add(arguments.get(i));
      }

      closure.define(varArgsName.lexeme, varArgs);
    }

    // **kwargs (last arg is expected to be Map<String, Object>)
    if (hasVarKwargs) {
      Object lastArg = arguments.get(arguments.size() - 1);
      if (!(lastArg instanceof Map)) {
        throw new RuntimeError(varArgsName,
            "**kwargs must be passed as map.");
      }
      closure.define(kwArgsName.lexeme, lastArg);
    }

    try {
      interpreter.executeBlock(body, closure);

    // catch-return
    } catch (Return returnValue) {
      // Classes early-return-this
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

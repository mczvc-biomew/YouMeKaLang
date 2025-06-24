package io.github.yumika;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YmkFunction implements YmkCallable {
  private final String name;
  private final List<Token> params;
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
    this.body = declaration.body;
    this.hasVarArgs = declaration.hasVarArgs;
    this.hasVarKwargs = declaration.hasVarKwargs;
    this.varArgsName = declaration.varArgsName;
    this.kwArgsName = declaration.kwArgsName;
  }

  YmkFunction(Expr.Function expression, Environment closure,  boolean isInitializer) {
    this.name = null;
    this.params = expression.params;
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
    Environment environment = new Environment(closure);
    int normalParamCount = params.size();
    // standard args
    // Normal arguments
    for (int i = 0; i < normalParamCount && i < arguments.size(); i++) {
      environment.define(params.get(i).lexeme,
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

      environment.define(varArgsName.lexeme, varArgs);
    }

    // **kwargs (last arg is expected to be Map<String, Object>)
    if (hasVarKwargs) {
      Object lastArg = arguments.get(arguments.size() - 1);
      if (!(lastArg instanceof Map)) {
        throw new RuntimeError(varArgsName,
            "**kwargs must be passed as map.");
      }
      environment.define(kwArgsName.lexeme, lastArg);
    }

    try {
      interpreter.executeBlock(body, environment);

    // catch-return
    } catch (Return returnValue) {
      // Classes early-return-this
      if (isInitializer) return closure.getAt(0, "this");

      return returnValue.value;
    }

    // Classes return-this
    if (isInitializer) return closure.getAt(0, "this");
    return null;
  }
}

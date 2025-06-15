package io.github.yumika;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class YmkFunction implements YmkCallable {
  private final Stmt.Function declaration;
  private final Environment closure;

  private final boolean isInitializer;

  YmkFunction(Stmt.Function declaration, Environment closure,
              boolean isInitializer) {
    this.isInitializer = isInitializer;
    // closure-constructor
    this.closure = closure;
    this.declaration = declaration;
  }

  YmkFunction bind(YmkInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);

    return new YmkFunction(declaration, environment,
        isInitializer);
  }

  @Override
  public String toString() { return "<fn " + declaration.name.lexeme + ">"; }

  @Override
  public int arity() { return declaration.params.size(); }

  // function-call
  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Environment environment = new Environment(closure);
    int normalParamCount = declaration.params.size();
    // standard args
    // Normal arguments
    for (int i = 0; i < normalParamCount && i < arguments.size(); i++) {
      environment.define(declaration.params.get(i).lexeme,
          arguments.get(i));
    }

    if (declaration.hasVarArgs && declaration.varArgsName == null) {
      throw new RuntimeError(null,
          "Must have var args name.");
    }
    if (declaration.hasVarKwargs && declaration.kwArgsName == null) {
      throw new RuntimeError(null,
          "Must have kwargs name.");
    }

    // *args
    if (declaration.hasVarArgs) {
      List<Object> varArgs = new ArrayList<>();
      for (int i = normalParamCount; i < arguments.size(); i++) {
        varArgs.add(arguments.get(i));
      }

      environment.define(declaration.varArgsName.lexeme, varArgs);
    }

    // **kwargs (last arg is expected to be Map<String, Object>)
    if (declaration.hasVarKwargs) {
      Object lastArg = arguments.get(arguments.size() - 1);
      if (!(lastArg instanceof Map)) {
        throw new RuntimeError(declaration.varArgsName,
            "**kwargs must be passed as map.");
      }
      environment.define(declaration.kwArgsName.lexeme, lastArg);
    }

    try {
      interpreter.executeBlock(declaration.body, environment);

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

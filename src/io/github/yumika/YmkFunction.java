package io.github.yumika;

import java.util.List;

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

    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme,
          arguments.get(i));
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

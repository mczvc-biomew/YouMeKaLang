package io.github.yumika;

import java.util.List;

class YmkLambda implements YmkCallable {
  private final Expr.Lambda declaration;
  private final Environment closure;
  private final Object thisContext;

  public YmkLambda(Expr.Lambda declaration, Environment closure, Object thisContext) {
    this.declaration = declaration;
    this.closure = closure;
    this.thisContext = thisContext;
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  public YmkLambda bind(Object newThis) {
    return new YmkLambda(this.declaration, this.closure, newThis);
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }
    if (thisContext != null) {
      environment.define("this", thisContext);
    }
    return interpreter.evaluateExpr(declaration.body, environment);
  }

  @Override
  public String toString() { return "<lambda fn>"; }

}

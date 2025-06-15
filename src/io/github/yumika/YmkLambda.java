package io.github.yumika;

import java.util.List;

class YmkLambda implements YmkCallable {
  private final Expr.Lambda lambda;
  private final Environment closure;

  public YmkLambda(Expr.Lambda lambda, Environment closure) {
    this.lambda = lambda;
    this.closure = closure;
  }

  @Override
  public int arity() {
    return lambda.params.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < lambda.params.size(); i++) {
      environment.define(lambda.params.get(i).lexeme, arguments.get(i));
    }
    return interpreter.evaluateExpr(lambda.body, environment);
  }

  @Override
  public String toString() { return "<lambda fn>"; }

}

package io.github.yumika;

import java.util.List;
import java.util.Map;

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
  public Object call(Interpreter interpreter, List<Object> arguments,
                     Map<String, Object> kwargs) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < declaration.params.size(); i++) {
      Token param = declaration.params.get(i);
      Token paramType = declaration.paramTypes.get(i);
      Object argValue = arguments.get(i);

      if (paramType != null) {
        String expected = paramType.lexeme;
        if (!interpreter.isTypeMatch(argValue, expected)) {
          throw new RuntimeError(param,
              "TypeError: Expected argument of type '" + expected + "', got '" +
                  interpreter.getTypeName(argValue) + "'");
        }
      }
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }
    if (thisContext != null) {
      environment.define("this", thisContext);
    }
    Object returnValue = interpreter.evaluateExpr(declaration.body, environment);
    if (declaration.returnType != null) {
      String expected = declaration.returnType.lexeme;
      if (!interpreter.isTypeMatch(returnValue, expected)) {
        throw new RuntimeError(declaration.returnType,
            "TypeError: Expected return of type '" + expected + "', got '" +
                interpreter.getTypeName(returnValue) + "'");
      }
    }
    return returnValue;
  }

  @Override
  public String toString() { return "<lambda fn>"; }

}

package io.github.yumika;

import java.util.List;

class YmkEnv implements YmkCallable {
  @Override
  public int arity() {
    return 0;
  }
  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    return interpreter.getEnvironment();
  }
}

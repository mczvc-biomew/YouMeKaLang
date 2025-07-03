package io.github.yumika;

import java.util.List;
import java.util.Map;

class YmkEnv implements YmkCallable {
  @Override
  public int arity() {
    return 0;
  }
  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments,
                     Map<String, Object> kwargs) {
    return interpreter.getEnvironment();
  }
}

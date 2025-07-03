package io.github.yumika;

import java.util.List;
import java.util.Map;

public interface YmkCallable {
  int arity();
  Object call(Interpreter interpreter, List<Object> args, Map<String, Object> kwargs);
}

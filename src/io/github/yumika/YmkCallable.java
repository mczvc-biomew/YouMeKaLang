package io.github.yumika;

import java.util.List;

public interface YmkCallable {
  int arity();
  Object call(Interpreter interpreter, List<Object> args);
}

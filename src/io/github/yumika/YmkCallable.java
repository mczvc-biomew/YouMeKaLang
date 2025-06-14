package io.github.yumika;

import java.util.List;

interface YmkCallable {
  int arity();
  Object call(Interpreter interpreter, List<Object> arguments);
}

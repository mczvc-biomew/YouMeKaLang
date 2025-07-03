package io.github.yumika;

import java.util.List;
import java.util.Map;

// Define NativeFunction adapter that wraps a lambda as a YmkFunction
public class YmkNativeFunction implements YmkCallable {
  private final String name;
  private final int arity;
  private final NativeImpl impl;

  @FunctionalInterface
  public interface NativeImpl {
    Object call(Interpreter interpreter, List<Object> args);
  }

  public YmkNativeFunction(String name, int arity, NativeImpl impl) {
    this.name = name;
    this.arity = arity;
    this.impl = impl;
  }

  @Override
  public int arity() {
    return arity;
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments,
                     Map<String, Object> kwargs) {
    return impl.call(interpreter, arguments);
  }

  @Override
  public String toString() {
    return "<native fn>";
  }
}

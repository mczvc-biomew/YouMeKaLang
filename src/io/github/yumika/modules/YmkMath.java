package io.github.yumika.modules;

import io.github.yumika.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YmkMath extends YmkInstance {

  public YmkMath(Interpreter interpreter) {
    super(buildMathClass(interpreter));
    defineConstants(interpreter);
  }

  private static YmkClass buildMathClass(Interpreter interpreter) {
    Map<String, YmkFunction> methods = new HashMap<>();
    return new YmkClass("Math", null, methods, false);
  }

  private static double toDouble(List<Object> args, int index) {
    Object val = args.get(index);
    if (val instanceof Double) return (Double) val;
    if (val instanceof Integer) return ((Integer) val).doubleValue();
    throw new RuntimeException("Expected a number at index " + index);
  }

  private void defineConstants(Interpreter interpreter) {
    this.set(new Token(TokenType.IDENTIFIER, "PI", null, 0), Math.PI, interpreter);
    this.set(new Token(TokenType.IDENTIFIER, "E", null, 0), Math.E, interpreter);

    defineFunction("sqrt", 1, (_interpreter, args) -> Math.sqrt(toDouble(args, 0)), interpreter);
    defineFunction("pow", 2, (_interpreter, args) -> Math.pow(toDouble(args, 0), toDouble(args, 1)), interpreter);
    defineFunction("abs", 1, (_interpreter, args) -> Math.abs(toDouble(args, 0)), interpreter);
    defineFunction("floor", 1, (_interpreter, args) -> Math.floor(toDouble(args, 0)), interpreter);
    defineFunction("ceil", 1, (_interpreter, args) -> Math.ceil(toDouble(args, 0)), interpreter);
  }

  private void defineFunction(String name, int arity, YmkNativeFunction.NativeImpl impl, Interpreter interpreter) {
    set(id(name), new YmkNativeFunction(name, arity, impl), interpreter);
  }

  private Token id(String name) {
    return new Token(TokenType.IDENTIFIER, name, null, 0);
  }

}

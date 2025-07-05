package io.github.yumika;

import java.util.List;

public class GeneratorFrame {
  private final Interpreter interpreter;
  private final List<Stmt> stmts;
  private final Environment closure;

  private int stmtIndex = 0;
  private boolean isDone = false;

  public GeneratorFrame(Stmt.Function declaration, Environment closure) {
    this.stmts = declaration.body;
    this.closure = new Environment(closure);
    this.interpreter = new Interpreter();
    this.interpreter.environment = this.closure;
  }

  public Object resume() {
    if (isDone) throw new GeneratorInterpreter.GeneratorComplete();
    try {
      while (stmtIndex < stmts.size()) {
        interpreter.execute(stmts.get(stmtIndex));
        stmtIndex++;
      }
      isDone = true;
      throw new GeneratorInterpreter.GeneratorComplete();
    } catch (GeneratorInterpreter.YieldException y) {
      return y.value;
    }
  }
}

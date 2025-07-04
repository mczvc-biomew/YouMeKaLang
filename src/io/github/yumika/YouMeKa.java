package io.github.yumika;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class YouMeKa {

  private static final Interpreter interpreter = new Interpreter();
  // had-error
  static boolean hadError = false;
  // Evaluating Expressions had-runtime-error-field
  static boolean hadRuntimeError = false;

  public static void main(String[] args) throws IOException, InterruptedException {
    System.out.printf("Hello and welcome!\n");

    if (args.length > 1) {
      System.out.println("Usage: ymk [script]");
      System.exit(64);
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String fileName) throws IOException, InterruptedException {
//    Console console = new Console();
//    console.setVisible(true);

    byte[] bytes = Files.readAllBytes(Paths.get(fileName));
    run(new String(bytes, Charset.defaultCharset()));

    // Indicate an error in the exit code;
    if (hadError) System.exit(65);

    // Evaluating Expressions check-runtime-error
    if (hadRuntimeError) System.exit(70);

  }

  private static void runPrompt() throws IOException, InterruptedException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);


    while (true) {
      System.out.print("ymkb> ");
      String line = reader.readLine();

      if (line == null) break;
      run(line);

      hadError = false;
    }
  }

  private static void run(String source) throws InterruptedException {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

//    // For now, just print tokens.
//    for (Token token : tokens) {
//      System.out.println(token);
//    }

    Parser parser = new Parser(tokens);
    List<Stmt> statements = parser.parse();

    // Stop if there was a syntax error.
    if (hadError) return;

    // Resolving and Binding create-resolver
    Resolver resolver = new Resolver(interpreter);
    resolver.resolve(statements);

    // Stop if there was a resolution error.
    if (hadError) return;

    try {

      interpreter.initGlobalDefinitions(interpreter.globals);
      interpreter.initJavaPackage(interpreter.globals);
      interpreter.interpret(statements);

      while (interpreter.runningTimers.get() > 0) {
//        System.out.println("Shutting down in (" + interpreter.runningTimers.get() * 50 + "ms)...");
        Thread.sleep(100);
      }
    } finally {
      interpreter.shutdownScheduler();
    }

  }

  static void error(int line, String message) { report(line, "", message); }

  private static void report(int line, String where, String message) {
    System.err.println("[line " + line + "] Error" + where + ": " + message);
    hadError = true;
  }

  static void error(Token token, String message) {
    if (token.type == TokenType.EOF) {
      report(token.line, " at end", message);
    } else {
      report(token.line, " at '" + token.lexeme + "'", message);
    }
  }

  static void runtimeError(RuntimeError error) {
    System.err.println(error.getMessage() +
        (error.token != null ?
          "\n[line " + error.token.line + "]"
        : "."));
    hadRuntimeError = true;
  }
}
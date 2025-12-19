package io;

import ast.Expr;
import java.nio.file.*;

public class DemoLoadEval {
    public static void main(String[] args) throws Exception {
        String path = (args.length > 0) ? args[0] : "samples/expr/add_mul.json";
        String json = Files.readString(Path.of(path));
        Expr e = AstJson.parseRoot(json);

        double x = 1.0;
        double v = e.eval(x);

        System.out.println("file=" + path);
        System.out.println("x=" + x);
        System.out.println("value=" + v);
    }
}

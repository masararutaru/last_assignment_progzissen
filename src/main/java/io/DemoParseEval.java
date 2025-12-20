package io;

import ast.Expr;
import parse.Parser;

public class DemoParseEval {
    public static void main(String[] args) {
        String s = (args.length > 0) ? args[0] : "1+2*3";
        Expr e = Parser.parse(s);

        double x = 2.0;
        double v = e.eval(x);

        System.out.println("expr=" + s);
        System.out.println("x=" + x);
        System.out.println("value=" + v);
    }
}


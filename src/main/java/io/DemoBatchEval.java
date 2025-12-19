package io;

import ast.Expr;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public class DemoBatchEval {

    static class Case {
        String file;
        double x;
        double expected;
        double tol;
        int line;
    }

    public static void main(String[] args) throws Exception {
        String csvPath = (args.length >= 1) ? args[0] : "tests/phase1_tests.csv";
        List<Case> cases = loadCsv(csvPath);

        int ok = 0, ng = 0;
        for (Case c : cases) {
            try {
                String json = Files.readString(Path.of(c.file));
                Expr expr = AstJson.parseRoot(json);

                double actual = expr.eval(c.x);
                boolean pass = closeEnough(actual, c.expected, c.tol);

                if (pass) {
                    ok++;
                    System.out.printf("[OK] line=%d file=%s x=%s actual=%.15f expected=%.15f tol=%g%n",
                            c.line, c.file, c.x, actual, c.expected, c.tol);
                } else {
                    ng++;
                    System.out.printf("[NG] line=%d file=%s x=%s actual=%.15f expected=%.15f tol=%g diff=%g%n",
                            c.line, c.file, c.x, actual, c.expected, c.tol, Math.abs(actual - c.expected));
                }
            } catch (Exception e) {
                ng++;
                System.out.printf("[ERR] line=%d file=%s : %s%n", c.line, c.file, e.toString());
            }
        }

        System.out.printf("%nRESULT: ok=%d ng=%d total=%d%n", ok, ng, ok + ng);
        if (ng != 0) System.exit(1);
    }

    static boolean closeEnough(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    static List<Case> loadCsv(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        List<Case> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (i == 0 && line.toLowerCase().startsWith("file,")) continue; // header

            String[] parts = line.split(",", -1);
            if (parts.length < 4) throw new IllegalArgumentException("bad csv line " + (i+1) + ": " + line);

            Case c = new Case();
            c.file = parts[0].trim();
            c.x = Double.parseDouble(parts[1].trim());
            c.expected = Double.parseDouble(parts[2].trim());
            c.tol = Double.parseDouble(parts[3].trim());
            c.line = i + 1;
            out.add(c);
        }
        return out;
    }
}

package io;

import ast.Expr;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public class DemoBatchEvalError {

    static class ErrorCase {
        String file;
        double x;
        String expectedError; // 部分一致でOK
        int line;
    }

    public static void main(String[] args) throws Exception {
        String csvPath = (args.length >= 1) ? args[0] : "tests/phase1_tests_error.csv";
        List<ErrorCase> cases = loadCsv(csvPath);

        int ok = 0, ng = 0;
        for (ErrorCase c : cases) {
            try {
                String json = Files.readString(Path.of(c.file));
                Expr expr = AstJson.parseRoot(json);
                double actual = expr.eval(c.x);
                
                // エラーが期待されているのに例外が発生しなかった
                ng++;
                System.out.printf("[NG] line=%d file=%s x=%s : Expected error '%s' but got result=%.15f%n",
                        c.line, c.file, c.x, c.expectedError, actual);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains(c.expectedError)) {
                    ok++;
                    System.out.printf("[OK] line=%d file=%s x=%s : Got expected error '%s'%n",
                            c.line, c.file, c.x, c.expectedError);
                } else {
                    ng++;
                    System.out.printf("[NG] line=%d file=%s x=%s : Expected error '%s' but got '%s'%n",
                            c.line, c.file, c.x, c.expectedError, errorMsg != null ? errorMsg : e.getClass().getSimpleName());
                }
            }
        }

        System.out.printf("%nRESULT: ok=%d ng=%d total=%d%n", ok, ng, ok + ng);
        if (ng != 0) System.exit(1);
    }

    static List<ErrorCase> loadCsv(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        List<ErrorCase> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (i == 0 && line.toLowerCase().startsWith("file,")) continue; // header

            String[] parts = line.split(",", -1);
            if (parts.length < 3) throw new IllegalArgumentException("bad csv line " + (i+1) + ": " + line);

            ErrorCase c = new ErrorCase();
            c.file = parts[0].trim();
            c.x = Double.parseDouble(parts[1].trim());
            c.expectedError = parts[2].trim();
            c.line = i + 1;
            out.add(c);
        }
        return out;
    }
}


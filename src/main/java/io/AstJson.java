package io;

import ast.*;
import org.json.*;
import java.util.*;

public class AstJson {

    public static Expr parseRoot(String jsonText) {
        JSONObject root = new JSONObject(jsonText);
        int version = root.getInt("version");
        if (version != 1) throw new IllegalArgumentException("Unsupported version: " + version);
        return parseExpr(root.getJSONObject("expr"));
    }

    public static Expr parseExpr(JSONObject obj) {
        String type = obj.getString("type");
        switch (type) {
            case "Num":
                return new Num(obj.getDouble("value"));
            case "Sym":
                return new Sym(obj.getString("name"));
            case "Add":
                return new Add(parseArgs(obj.getJSONArray("args")));
            case "Mul":
                return new Mul(parseArgs(obj.getJSONArray("args")));
            case "Div":
                return new Div(parseArgs(obj.getJSONArray("args")));
            case "Pow":
                return new Pow(parseArgs(obj.getJSONArray("args"))); // argsは2要素想定
            case "Func": {
                String name = obj.getString("name");
                List<Expr> args = parseArgs(obj.getJSONArray("args"));
                return new Func(name, args);
            }
            
            default:
                throw new IllegalArgumentException("Unknown Expr type: " + type);
        }
    }

    private static List<Expr> parseArgs(JSONArray arr) {
        List<Expr> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseExpr(arr.getJSONObject(i)));
        }
        return list;
    }

    /**
     * ASTをJSON形式に変換する（version 1形式）
     * @param expr 変換するAST
     * @return JSONObject（{"version": 1, "expr": {...}} 形式）
     */
    public static JSONObject toJsonV1(Expr expr) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("expr", toJsonExpr(expr));
        return root;
    }

    /**
     * ExprノードをJSONObjectに変換する
     */
    private static JSONObject toJsonExpr(Expr expr) {
        JSONObject obj = new JSONObject();

        if (expr instanceof Num) {
            Num n = (Num) expr;
            obj.put("type", "Num");
            obj.put("value", n.value);
        } else if (expr instanceof Sym) {
            Sym s = (Sym) expr;
            obj.put("type", "Sym");
            obj.put("name", s.name);
        } else if (expr instanceof Add) {
            Add a = (Add) expr;
            obj.put("type", "Add");
            obj.put("args", toJsonArgs(a.args));
        } else if (expr instanceof Mul) {
            Mul m = (Mul) expr;
            obj.put("type", "Mul");
            obj.put("args", toJsonArgs(m.args));
        } else if (expr instanceof Div) {
            Div d = (Div) expr;
            obj.put("type", "Div");
            JSONArray args = new JSONArray();
            args.put(toJsonExpr(d.getLeft()));
            args.put(toJsonExpr(d.getRight()));
            obj.put("args", args);
        } else if (expr instanceof Pow) {
            Pow p = (Pow) expr;
            obj.put("type", "Pow");
            JSONArray args = new JSONArray();
            args.put(toJsonExpr(p.getBase()));
            args.put(toJsonExpr(p.getExp()));
            obj.put("args", args);
        } else if (expr instanceof Func) {
            Func f = (Func) expr;
            obj.put("type", "Func");
            obj.put("name", f.getName());
            obj.put("args", toJsonArgs(f.getArgs()));
        } else {
            throw new IllegalArgumentException("Unknown Expr type: " + expr.getClass().getName());
        }

        return obj;
    }

    /**
     * ExprのリストをJSONArrayに変換する
     */
    private static JSONArray toJsonArgs(List<Expr> args) {
        JSONArray arr = new JSONArray();
        for (Expr e : args) {
            arr.put(toJsonExpr(e));
        }
        return arr;
    }
}

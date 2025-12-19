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
}

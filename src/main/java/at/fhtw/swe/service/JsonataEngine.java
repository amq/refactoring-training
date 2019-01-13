package at.fhtw.swe.service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
/**
 * Run JSONATA-runtime on jvm-nashorn
 * https://stackoverflow.com/questions/40416032/how-can-i-use-jsonata-in-java
 */
public class JsonataEngine {

    private static final Logger LOG = LoggerFactory.getLogger(JsonataEngine.class);

    private ScriptEngine engine;
    private Invocable inv;

    public JsonataEngine() {
        final ScriptEngineManager factory = new ScriptEngineManager();
        engine = factory.getEngineByName("JavaScript");
        inv = (Invocable) engine;

        try {
            final Reader jsonata =
                    new InputStreamReader(
                            this.getClass().getResourceAsStream("/static/jsonata-1.5.4-es5.js"),
                            StandardCharsets.UTF_8.name());
            engine.eval(jsonata);
        } catch (UnsupportedEncodingException ue) {
            throw new RuntimeException("Jsonata file could not be loaded", ue);
        } catch (ScriptException se) {
            throw new RuntimeException("Jsonata could not be initialized", se);
        }
    }

    public Object parseData(String data) {
        engine.put("input", data);
        try {
            return engine.eval("JSON.parse(input);");
        } catch (ScriptException e) {
            throw new RuntimeException("Error when parsing json input", e);
        }
    }

    public String validate(Object data, String jsonataExpression) {

        Object resultjson = null;
        try {
            final Object expr = inv.invokeFunction("jsonata", jsonataExpression);
            resultjson = inv.invokeMethod(expr, "evaluate", data);
            engine.put("resultjson", resultjson);
            return engine.eval("JSON.stringify(resultjson);").toString();
        } catch (ScriptException | NoSuchMethodException e) {
            LOG.error("the {} doesn't match with {} pattern", resultjson, jsonataExpression);
            throw new RuntimeException("Jsonata could not be called", e);
        }
    }
}

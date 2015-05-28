package com.mycompany.app;
import org.rythmengine.*;
import java.util.*;
/**
 * A simple application demonstrate basic usage of Rythm Template Engine
 */
public class App {
    private static RythmEngine engine;

    private static void echo(String msg, Object ... args) {
        System.out.println(String.format(msg, args));
    }

    private static void init() {
        echo("initializing rythmengine");
        Map<String, Object> conf = new HashMap<String, Object>();
        conf.put("home.template", "templates");
        engine = new RythmEngine(conf);
        echo("engine initialized");
    }

    private static void renderWithParamByName() {
        echo("\nrender template with params by name using a map");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("foo", "FOO");
        params.put("bar", "BAR");
        String result = engine.render("MyTemplate.xml", params);
        echo(result);
    }

    private static void renderWithParamByPosition() {
        echo("\nrender template with params by position using array");
        String result = engine.render("MyTemplate.xml", "foo", "bar");
        echo(result);
    }

    private static void doJob() {
        echo("start doing real job now...");
        renderWithParamByName();
        renderWithParamByPosition();
    }

    public static void main( String[] args ) {
        init();
        doJob();
    }
}
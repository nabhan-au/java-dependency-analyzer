package org.analyzer;

import com.github.javaparser.ast.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class MyTypeSolvers {

    public static List<String> splitStructuredTypes(String input) {
        List<String> components = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int balance = 0; // To track nested generics

        for (char c : input.toCharArray()) {
            if (c == '<') {
                if (!current.isEmpty()) {
                    components.add(current.toString().trim());
                    current.setLength(0);
                }
                balance++;
            } else if (c == '>') {
                if (!current.isEmpty()) {
                    components.add(current.toString().trim());
                    current.setLength(0);
                }
                balance--;
            } else if (c == ',') {
                if (!current.isEmpty()) {
                    components.add(current.toString().trim());
                    current.setLength(0);
                }
            } else if (c == '&') {
                if (!current.isEmpty()) {
                    components.add(current.toString().trim());
                    current.setLength(0);
                }
            } else if (c == ' ' && balance > 0) { // Preserve space-separated keywords inside generics
                if (!current.isEmpty()) {
                    components.add(current.toString().trim());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        // Add any remaining type
        if (!current.isEmpty()) {
            components.add(current.toString().trim());
        }
        components = components.stream().filter(c -> !c.equals("extends") & !c.equals("super") & !c.equals("implement") & !c.equals("permits") & !c.equals("?")).toList();
        components = components.stream().map(c -> {
            try {
                return shortedForm(c);
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).map(MyTypeSolvers::getScope).filter(Objects::nonNull).toList();
        return components;
    }

    public static String shortedForm(String type) {
        if (type.trim().endsWith("[]")) {
            if (type.matches("\\[\\]+")) {
                return null; // Return null if it contains only brackets
            }
            // Remove trailing square brackets and return the processed value
            var result = type.replaceAll("\\[\\]+$", "");
            if (result.isEmpty()) {
                return null;
            }
            return result.trim();
        }
        return type;
    }

    public static String getScope(String type) {
        if (type.trim().contains(".")) {
            String[] parts = type.split("\\.", 2);
            var result = parts[0].trim();
            if (result.isEmpty()) {
                return null;
            }
            return result.trim();
        }
        return type;
    }


    public static boolean isShadowClassPresented(Node node, String className) {
        var parentNode = node.getParentNode();
        parentNode.get();
        if (parentNode.isEmpty()) {
            return false;
        }
        return true;
    }

    public static void isShadowMethodPresented(Node node, String methodName) {

    }

    public static void findShadowField(Node node, String fieldName) {

    }

    public static void test(String testString) {
        var result = splitStructuredTypes(testString);
        AtomicInteger count = new AtomicInteger();
        result.forEach(res -> {
            count.set(count.get() + 1);
            System.out.println(count + ": " + res);

        });
    }
}

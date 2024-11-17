package org.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyTypeSolvers {

//    public static List<String> extractCompoundType(String type) {
//        List<String> types = new ArrayList<>();
//        String genericRegex = "^(\\w+)<(.+)>$";
//        Pattern genericPattern = Pattern.compile(genericRegex);
//        Matcher genericMatcher = genericPattern.matcher(type);
//
//        // Check array type
//        if (type.endsWith("[]")) {
//            String arrayRegex = "^([\\w<>?,\\s]+)(\\[\\])*";
//            Pattern arrayPattern = Pattern.compile(arrayRegex);
//            Matcher matcher = arrayPattern.matcher(type);
//            if (matcher.find()) {
//                var baseType = matcher.group(1);
//                types.addAll(extractCompoundType(baseType));
//            } else {
//                throw new IllegalArgumentException("Invalid compound type: " + type);
//            }
//        } else if (genericMatcher.find()) {
//            // Group 1: Base type
//            String baseType = genericMatcher.group(1);
//            types.add(baseType);
//
//
//            types.addAll(extractCompoundType("<" + genericMatcher.group(2) + ">"));
//        }
//        else if (type.startsWith("<") && type.endsWith(">")) {
//            var args = type.substring(1, type.length() - 1);
//            if (args.contains(",")) {
//                List<String> typeArguments = parseGenericArguments(args);
//                for (String arg : typeArguments) {
//                    types.addAll(extractCompoundType(arg));
//                }
//            } else if (args.startsWith("?")) {
//                if (type.contains("extends")) {
//                    String bound = type.split("extends", 2)[1].trim();
//                    types.addAll(extractCompoundType(bound));
//                } else if (type.contains("super")) {
//                    String bound = type.split("super", 2)[1].trim();
//                    types.addAll(extractCompoundType(bound));
//                }
//            }
//        }
//        else if (type.startsWith("?")) {
//            if (type.contains("extends")) {
//                String bound = type.split("extends", 2)[1].trim();
//                types.addAll(extractCompoundType(bound));
//            } else if (type.contains("super")) {
//                String bound = type.split("super", 2)[1].trim();
//                types.addAll(extractCompoundType(bound));
//            } else {
//                types.add("?"); // Just a wildcard
//            }
//        }
//        else if (type.contains("extends")) {
//            String[] parts = type.split(" extends ", 2);
//            types.addAll(extractCompoundType(parts[0].trim())); // Add the base type (e.g., "T")
//            types.addAll(extractCompoundType(parts[1].trim())); // Process the bound
//        }
//        else if (type.contains(".")) {
//            String[] parts = type.split("\\.", 2);
//            types.addAll(extractCompoundType(parts[0].trim()));
//        }
//        else {
//            types.add(type);
//        }
//        return types;
//    }
//
//    private static List<String> parseGenericArguments(String genericContent) {
//        List<String> arguments = new ArrayList<>();
//
//        int bracketCount = 0;
//        StringBuilder currentArg = new StringBuilder();
//        for (char c : genericContent.toCharArray()) {
//            if (c == '<') {
//                bracketCount++;
//            } else if (c == '>') {
//                bracketCount--;
//            } else if (c == ',' && bracketCount == 0) {
//                // Split at top-level commas
//                arguments.addAll(extractCompoundType(currentArg.toString().trim()));
//                currentArg.setLength(0);
//                continue;
//            }
//            currentArg.append(c);
//        }
//
//        // Add the last argument
//        if (currentArg.length() > 0) {
//            arguments.add(currentArg.toString().trim());
//        }
//
//        return arguments;
//    }

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

    public static void main(String[] args) {
        test("Test<Sting, Integer extends Temp<456.789 extends 123, Is>, Test<? super OPD, Is[]>>");
        test("Test<Sting extends Test, Integer extends Temp<456.123 extends 123, Is>, Test<? super OPD, Is[]>>");
        test("Test<lol>.you.3");
        test("Test<T super Test<T extends Y & Z>>[]");
    }
}

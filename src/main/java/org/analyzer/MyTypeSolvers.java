package org.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyTypeSolvers {

    public static List<String> extractCompoundType(String type) {
        List<String> types = new ArrayList<>();
        String genericRegex = "^(\\w+)<(.+)>$";
        Pattern genericPattern = Pattern.compile(genericRegex);
        Matcher genericMatcher = genericPattern.matcher(type);

        // Check array type
        if (type.endsWith("[]")) {
            String arrayRegex = "^([\\w<>?,\\s]+)(\\[\\])*";
            Pattern arrayPattern = Pattern.compile(arrayRegex);
            Matcher matcher = arrayPattern.matcher(type);
            if (matcher.find()) {
                var baseType = matcher.group(1);
                types.addAll(extractCompoundType(baseType));
            } else {
                throw new IllegalArgumentException("Invalid compound type: " + type);
            }
        } else if (genericMatcher.find()) {
            // Group 1: Base type
            String baseType = genericMatcher.group(1);
            types.add(baseType);


            types.addAll(extractCompoundType( "<" + genericMatcher.group(2) + ">"));
        }
        else if (type.startsWith("<") && type.endsWith(">")) {
            var args = type.substring(1, type.length() - 1);
            List<String> typeArguments = parseGenericArguments(args);
            for (String arg : typeArguments) {
                types.addAll(extractCompoundType(arg));
            }
        }
        else if (type.contains("extends")) {
            String[] parts = type.split(" extends ", 2);
            types.addAll(extractCompoundType(parts[0].trim())); // Add the base type (e.g., "T")
            types.addAll(extractCompoundType(parts[1].trim())); // Process the bound
        } else {
            types.add(type);
        }
        return types;
    }

    private static List<String> parseGenericArguments(String genericContent) {
        List<String> arguments = new ArrayList<>();

        int bracketCount = 0;
        StringBuilder currentArg = new StringBuilder();
        for (char c : genericContent.toCharArray()) {
            if (c == '<') {
                bracketCount++;
            } else if (c == '>') {
                bracketCount--;
            } else if (c == ',' && bracketCount == 0) {
                // Split at top-level commas
                arguments.addAll(extractCompoundType(currentArg.toString().trim()));
                currentArg.setLength(0);
                continue;
            }
            currentArg.append(c);
        }

        // Add the last argument
        if (currentArg.length() > 0) {
            arguments.addAll(extractCompoundType(currentArg.toString().trim()));
        }

        return arguments;
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

    public static void solve(VariableDeclarator variableDeclarator) {
        variableDeclarator.getType();
    }

    public static void main(String[] args) {
        var result = extractCompoundType("Test<Sting, Integer extends Temp<It, Is>, Test<It, Is[]>>");

        AtomicInteger count = new AtomicInteger();
        result.forEach(res -> {
            count.set(count.get() + 1);
            System.out.println(count + ": " + res);

        });
    }
}

package org.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GenerateImportGraph {

    private Map<String, Set<String>> importGraph = new HashMap<>();

    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("path/to/YourJavaFile.java");
        GenerateImportGraph generator = new GenerateImportGraph();
        generator.generateImportGraphForFunction(file, "yourMethodName");
        generator.printImportGraph();
    }

    public void generateImportGraphForFunction(File file, String methodName) throws FileNotFoundException {
        // Set up JavaParser with symbol resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);

        // Parse the file
        ParseResult<CompilationUnit> parseResult = parser.parse(file);
        if (!parseResult.getResult().isPresent()) {
            System.out.println("Could not parse file.");
            return;
        }
        CompilationUnit cu = parseResult.getResult().get();

        // Collect all import declarations
        Set<String> imports = new HashSet<>();
        Set<String> wildcardImports = new HashSet<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importDecl.isAsterisk()) {
                wildcardImports.add(importName);  // Save wildcard imports separately
            } else {
                imports.add(importName);
            }
        }

        // Visit the specific method
        cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .forEach(method -> analyzeMethodDependencies(method, imports, wildcardImports));
    }

    private void analyzeMethodDependencies(MethodDeclaration method, Set<String> imports, Set<String> wildcardImports) {
        // Visit each node inside the method to determine which imports are used
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(com.github.javaparser.ast.expr.NameExpr n, Void arg) {
                String className = n.getNameAsString();

                // Check if the class name matches an explicit import
                imports.stream()
                        .filter(importStr -> importStr.endsWith("." + className))
                        .forEach(importStr -> addDependency(method.getNameAsString(), importStr));

                // Handle wildcard imports by resolving the class
                wildcardImports.forEach(pkg -> resolveWildcardImport(pkg, className, method.getNameAsString()));
                super.visit(n, arg);
            }
        }, null);
    }

    private void resolveWildcardImport(String packageName, String className, String methodName) {
        try {
            // Attempt to resolve the fully qualified class name
            String fullyQualifiedName = packageName + "." + className;
            ResolvedReferenceTypeDeclaration typeDecl = new ReflectionTypeSolver()
                    .solveType(fullyQualifiedName).asReferenceType();

            // If successful, add it to the graph
            addDependency(methodName, typeDecl.getQualifiedName());
        } catch (Exception e) {
            // Class was not found in the package, do nothing
        }
    }

    private void addDependency(String methodName, String importStr) {
        importGraph.computeIfAbsent(methodName, k -> new HashSet<>()).add(importStr);
    }

    private void printImportGraph() {
        System.out.println("Import Graph:");
        importGraph.forEach((method, dependencies) -> {
            System.out.println("Method: " + method);
            dependencies.forEach(dep -> System.out.println("  -> " + dep));
        });
    }
}

package org.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenerateAST {
    public CompilationUnit getAST (File file, File jarFile) throws Exception {
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false),
                new JavaParserTypeSolver("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java"),
                new JarTypeSolver(jarFile));
        ParserConfiguration configuration = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser javaParser = new JavaParser(configuration);

        ParseResult<CompilationUnit> result = javaParser.parse(file);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new Exception("Cannot parse file: " + file.getAbsolutePath());
        }
        var cu = result.getResult().get();
        ImportResult importResult = extractImport(cu);

//        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
//        for (int i = methodCalls.size() - 1; i >= 0; i--) {
//            try {
//                MethodCallExpr methodCallExpr = methodCalls.get(i);
//                System.out.println("methodCallExpr = " + methodCallExpr + "\n" + "methodCallExpr.resolve() = " + methodCallExpr.resolve());
//                System.out.println("methodCallExpr.calculateResolvedType() = " + methodCallExpr.calculateResolvedType());
//            } catch (Exception e) {
//                System.out.println(e.getMessage());
//            }
//
//        }
        new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(MethodCallExpr methodCall, Object arg) {
                super.visit(methodCall, arg);
                try {
                    var test = methodCall.resolve();
                    System.out.println("test = " + test);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }.visit(cu, null);
//        Set<String> resolvedWildcardImports = new HashSet<>();
//        cu.accept(new VoidVisitorAdapter<Void>() {
//            @Override
//            public void visit(NameExpr node, Void arg) {
//                super.visit(node, arg);
//                String name = node.getNameAsString();
//
//
//                importResult.wildcardImports.forEach(pkg -> {
//                    try {
//                        String fullyQualifiedName = pkg + "." + name;
////                        System.out.println(fullyQualifiedName);
//                        typeSolver.solveType(fullyQualifiedName).asReferenceType();
//                        System.out.println(pkg + "." + name);
//                    } catch (Exception e) {
//                        // test
//                    }
//
//                });
//
//            }
//        }, null);
        return result.getResult().get();

    }

    private static ImportResult extractImport(CompilationUnit cu) {
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

        return new ImportResult(imports, wildcardImports);
    }

    private record ImportResult(Set<String> imports, Set<String> wildcardImports) {
    }


}

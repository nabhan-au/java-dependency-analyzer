package org.analyzer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectAnalyzer {

    public static void main(String[] args) throws IOException {
    	IWorkspace workspace = ResourcesPlugin.getWorkspace();
    	IWorkspaceRoot root = workspace.getRoot();
    	IProject[] projects = root.getProjects();
//        String projectDir = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java"; // Adjust to your project src path
//
//        List<File> javaFiles = new ArrayList<>();
//        Files.walk(Paths.get(projectDir))
//                .filter(path -> path.toString().endsWith(".java"))
//                .forEach(path -> javaFiles.add(path.toFile()));
//
//        for (File file : javaFiles) {
//            analyzeFile(file);
//        }
    }

    private static void analyzeFile(File file) throws IOException {
        String source = new String(Files.readAllBytes(file.toPath()));

        // Configure the parser for each file
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // Set up classpath and sourcepath for accurate resolution
        parser.setEnvironment(
                new String[] {
                        "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar",
                        System.getProperty("java.home") + "/lib/rt.jar" // Add JDK runtime if needed
                },// Classpath entries (jars or directories)
                new String[]{"/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src"}, // Sourcepath entries
                null,  // Encodings for each sourcepath entry, or null
                true   // Consider classpath entries as external to this project
        );

        // Set compiler options
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);

        // Parse and traverse the AST
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new ASTVisitor() {
//            @Override
//            public boolean visit(MethodDeclaration node) {
//                System.out.println("File: " + file.getName() + " - Method: " + node.getName());
//                return super.visit(node);
//            }

            @Override
            public boolean visit(MethodDeclaration node) {
                IMethodBinding binding = node.resolveBinding();
                if (binding != null) {
                    System.out.println("Method return type: " + binding.getReturnType().getName());
                } else {
                    System.out.println("Binding is null for method: " + node.getName());
                }
                return super.visit(node);
            }

            @Override
            public boolean visit(SingleVariableDeclaration node) {
                ITypeBinding typeBinding = node.getType().resolveBinding();
                if (typeBinding != null) {
                    System.out.println("Parameter: " + node.getName() + ", Type: " + typeBinding.getQualifiedName());
                } else {
                    System.out.println("Binding is null for parameter: " + node.getName() + ", Type: " + node.getType());
                }
                return super.visit(node);
            }

//            @Override
//            public boolean visit(VariableDeclarationFragment node) {
//                System.out.println("Variable: " + node.getName());
//                return super.visit(node);
//            }
//
//            @Override
//            public boolean visit(ImportDeclaration node) {
//                System.out.println("Import: " + node.getName());
//                return super.visit(node);
//            }
        });

        System.out.println(cu.toString());
    }
}


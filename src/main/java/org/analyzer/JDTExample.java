package org.analyzer;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class JDTExample {

    public static void main(String[] args) throws IOException {
        String filePath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/demo/NVCompDemo.java"; // Adjust to your file path
        String source = new String(Files.readAllBytes(Paths.get(filePath)));

        // Configure the parser
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest); // Use the latest Java version
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // Set compiler options
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options); // Use the appropriate Java version
        parser.setCompilerOptions(options);

        // Parse and visit the AST
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                System.out.println("Method: " + node.getName());
                return super.visit(node);
            }
        });
    }
}


package org.analyzer.models;

import com.github.javaparser.ast.ImportDeclaration;

import java.nio.file.Path;
import java.util.List;

public class DependencyResolverReport {
    public Path filePath;
    public List<ImportDeclaration> unusedImports;
    public List<ImportDeclaration> useImports;
    public List<ImportDeclaration> headerImports;
    public List<String> fullPathCalling;
    public List<SingleImportDetails> useImportObject;
    public List<ImportClassPath> failedImports;

    public DependencyResolverReport(Path filePath, List<ImportDeclaration> unusedImports, List<ImportDeclaration> usedImports, List<String> fullPathCalling, List<SingleImportDetails> useImportObject, List<ImportDeclaration> headerImports, List<ImportClassPath> failedImports) {
        this.filePath = filePath;
        this.unusedImports = unusedImports;
        this.useImports = usedImports;
        this.useImportObject = useImportObject;
        this.headerImports = headerImports;
        this.fullPathCalling = fullPathCalling;
        this.failedImports = failedImports;
    }
}

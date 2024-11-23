package org.analyzer.models;

import com.github.javaparser.ast.ImportDeclaration;

public class UnusedImportReport {
    public ImportDeclaration importDeclaration;
    public ImportClassPath importClassPath;
    public ImportArtifact fromArtifact = null;
    public Boolean isTransitive;

    public UnusedImportReport(ImportDeclaration importDeclaration, ImportArtifact fromArtifact, Boolean isTransitive) {
        this.importClassPath = new ImportClassPath(importDeclaration.toString().trim());
        this.importDeclaration = importDeclaration;
        this.fromArtifact = fromArtifact;
        this.isTransitive = isTransitive;
    }

    @Override
    public String toString() {
        return "UnusedImportReport [importDeclaration=" + importDeclaration.getNameAsString() + ", fromArtifact=" + fromArtifact + "]";
    }
}



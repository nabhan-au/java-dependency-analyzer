package org.analyzer.models;

import com.github.javaparser.ast.ImportDeclaration;

public class UnusedImportReport {
    public ImportDeclaration importDeclaration;
    public ImportClassPath importClassPath;
    public ImportArtifact fromArtifact = null;

    public UnusedImportReport(ImportDeclaration importDeclaration, ImportArtifact fromArtifact) {
        this.importClassPath = new ImportClassPath(importDeclaration.toString().trim());
        this.importDeclaration = importDeclaration;
        this.fromArtifact = fromArtifact;
    }

    @Override
    public String toString() {
        return "UnusedImportReport [importDeclaration=" + importDeclaration.getNameAsString() + ", fromArtifact=" + fromArtifact + "]";
    }
}



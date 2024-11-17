package org.analyzer.models;

import com.github.javaparser.ast.ImportDeclaration;

public class UseImportReport {
    public SingleImportDetails importDetails;
    public ImportArtifact importArtifact = null;

    public UseImportReport(SingleImportDetails importDetails, ImportArtifact importArtifact) {
        this.importDetails = importDetails;
        this.importArtifact = importArtifact;
    }

    @Override
    public String toString() {
        return "UseImportReport [importDetails=" + importDetails.importObject.toString() + ", importArtifact=" + importArtifact + "]";
    }
}

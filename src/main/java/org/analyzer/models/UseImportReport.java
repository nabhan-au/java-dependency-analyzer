package org.analyzer.models;

public class UseImportReport {
    public SingleImportDetails importDetails;
    public ImportArtifact fromArtifact = null;
    public Boolean isDirectDependency;

    public UseImportReport(SingleImportDetails importDetails, ImportArtifact fromArtifact, Boolean isDirectDependency) {
        this.importDetails = importDetails;
        this.fromArtifact = fromArtifact;
        this.isDirectDependency = isDirectDependency;
    }

    @Override
    public String toString() {
        return "UseImportReport [importDetails=" + importDetails.importObject.toString() + ", importArtifact=" + fromArtifact + "]";
    }
}

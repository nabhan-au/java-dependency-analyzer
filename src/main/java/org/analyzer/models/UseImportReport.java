package org.analyzer.models;

public class UseImportReport {
    public SingleImportDetails importDetails;
    public ImportArtifact fromArtifact = null;
    public Boolean isTransitive;

    public UseImportReport(SingleImportDetails importDetails, ImportArtifact fromArtifact, Boolean isTransitive) {
        this.importDetails = importDetails;
        this.fromArtifact = fromArtifact;
        this.isTransitive = isTransitive;
    }

    @Override
    public String toString() {
        return "UseImportReport [importDetails=" + importDetails.importObject.toString() + ", importArtifact=" + fromArtifact + "]";
    }
}

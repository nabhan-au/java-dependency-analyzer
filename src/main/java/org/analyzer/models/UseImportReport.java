package org.analyzer.models;

public class UseImportReport {
    public SingleImportDetails importDetails;
    public ImportArtifact fromArtifact = null;

    public UseImportReport(SingleImportDetails importDetails, ImportArtifact fromArtifact) {
        this.importDetails = importDetails;
        this.fromArtifact = fromArtifact;
    }

    @Override
    public String toString() {
        return "UseImportReport [importDetails=" + importDetails.importObject.toString() + ", importArtifact=" + fromArtifact + "]";
    }
}

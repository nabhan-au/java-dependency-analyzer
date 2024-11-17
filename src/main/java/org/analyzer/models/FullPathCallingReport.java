package org.analyzer.models;

public class FullPathCallingReport {
    public ImportClassPath importClassPath;
    public ImportArtifact fromArtifact = null;

    public FullPathCallingReport(ImportClassPath importClassPath, ImportArtifact fromArtifact) {
        this.importClassPath = importClassPath;
        this.fromArtifact = fromArtifact;
    }
}

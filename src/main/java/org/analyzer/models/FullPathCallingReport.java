package org.analyzer.models;

public class FullPathCallingReport {
    public ImportClassPath importClassPath;
    public ImportArtifact fromArtifact = null;
    public Boolean isTransitive;

    public FullPathCallingReport(ImportClassPath importClassPath, ImportArtifact fromArtifact, Boolean isTransitive) {
        this.importClassPath = importClassPath;
        this.fromArtifact = fromArtifact;
        this.isTransitive = isTransitive;
    }
}

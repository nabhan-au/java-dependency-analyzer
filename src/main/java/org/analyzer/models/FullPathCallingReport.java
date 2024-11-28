package org.analyzer.models;

public class FullPathCallingReport {
    public ImportClassPath importClassPath;
    public ImportArtifact fromArtifact = null;
    public Boolean isDirectDependency;

    public FullPathCallingReport(ImportClassPath importClassPath, ImportArtifact fromArtifact, Boolean isDirectDependency) {
        this.importClassPath = importClassPath;
        this.fromArtifact = fromArtifact;
        this.isDirectDependency = isDirectDependency;
    }
}

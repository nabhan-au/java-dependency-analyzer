package org.analyzer.models.json;

import org.analyzer.models.ImportArtifact;

public class ImportArtifactJson {
    public String artifact;
    public String directory;

    public ImportArtifactJson(ImportArtifact importArtifact) {
        this.artifact = (importArtifact == null ? "not from main direct dependency" : importArtifact.toString());
        this.directory = (importArtifact == null ? "not from main direct dependency" : importArtifact.getArtifactPath());
    }
}

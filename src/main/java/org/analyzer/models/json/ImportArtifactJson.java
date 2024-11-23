package org.analyzer.models.json;

import org.analyzer.models.ImportArtifact;

public class ImportArtifactJson {
    public String artifact;
    public String directory;

    public ImportArtifactJson(ImportArtifact importArtifact) {
        this.artifact = (importArtifact == null ? "Cannot detect from dependency" : importArtifact.toString());
        this.directory = (importArtifact == null ? "Cannot detect from dependency" : importArtifact.getArtifactPath());
    }
}

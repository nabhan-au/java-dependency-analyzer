package org.analyzer.models;

import java.util.Objects;

public class ImportArtifact {
    private String artifactId;
    private String groupId;
    private String version;
    private String artifactPath = "";

    public ImportArtifact(String artifactId, String groupId, String version) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
    }

    public ImportArtifact(String artifactId, String groupId, String version, String artifactPath) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
        this.artifactPath = artifactPath;
    }

    public ImportArtifact(ImportArtifact importArtifact) {
        this.artifactId = importArtifact.artifactId;
        this.groupId = importArtifact.getGroupId();
        this.version = importArtifact.version;
    }

    public ImportArtifact(Dependency dependency) {
        var splitDependency = dependency.getName().split(":");
        this.artifactId = splitDependency[1];
        this.groupId = splitDependency[0];
        this.version = dependency.getVersion();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public int hashCode() {
        return (groupId + ":" + artifactId + ":" + version).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // Step 1: Check for reference equality
        if (this == obj) {
            return true;
        }

        // Step 2: Check for null and class compatibility
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        // Step 3: Cast and compare significant fields
        ImportArtifact artifact = (ImportArtifact) obj;
        return Objects.equals(groupId, artifact.groupId) && Objects.equals(artifactId, artifact.artifactId) && Objects.equals(version, artifact.version);
    }
}

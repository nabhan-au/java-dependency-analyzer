package org.analyzer;

import org.analyzer.models.Dependency;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryUtils {
    Neo4jConnector connector;

    public RepositoryUtils(Neo4jConnector connector) {
        this.connector = connector;
    }

    public void getVersionWithArtifact(String artifactId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("artifactId", "artifactId");
        var result = connector.runQuery("MATCH (a:Artifact {id: $artifactId})-[:relationship_AR]-(r:Release) RETURN r.version", parameters);
        System.out.println(result);
    }

    public void test() {
        var result = connector.runQuery("MATCH (r:Release {id: 'com.thinkaurelius.titan:titan-jre6:0.4.3'})-[d:dependency]-(a:Artifact) RETURN d.targetVersion as version, a.id as id");
        System.out.println(result);
    }

    public List<Dependency> getDependencyFromReleaseId(String releaseId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("releaseId", releaseId);
        var result = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) WHERE d.scope <> 'test' AND d.scope <> 'runtime' RETURN d.targetVersion as version, a.id as id", parameters);
        return result.records().stream().map(r -> {
            var artifact = r.get("id").asString();
            var version = r.get("version").asString();
            return new Dependency(artifact, version);
        }).toList();
    }

    public List<Dependency> getTransitiveDependency(Dependency dependency, Integer level) {
        List<Dependency> result = new ArrayList<>();
        result.add(dependency);
        if (level >= 3) {
            return result;
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("releaseId", dependency.toString());
        var response = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) WHERE d.scope <> 'test' AND d.scope <> 'runtime' RETURN d.targetVersion as version, a.id as id", parameters);
        response.records().stream().forEach(r -> {
            var artifact = r.get("id").asString();
            var version = r.get("version").asString();
            var dep = new Dependency(artifact, version);
            result.addAll(getTransitiveDependency(dep, level + 1));
        });
        return result.stream().distinct().toList();
    }

    public static String getProjectVersions(String projectPath) {
        try {
            // Specify the Maven command to get the project version
            String[] command = {
                    "mvn", "help:evaluate",
                    "-Dexpression=project.version",
                    "-q", "-DforceStdout"
            };

            // Start the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(new File(projectPath));
            Process process = processBuilder.start();

            // Read the output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    return line;
                }
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Error: Maven command failed with exit code " + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}

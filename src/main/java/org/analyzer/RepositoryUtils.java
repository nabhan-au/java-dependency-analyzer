package org.analyzer;

import org.analyzer.models.Dependency;
import org.analyzer.models.ImportArtifact;

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
        var result = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) WHERE d.scope <> 'test' RETURN d.targetVersion as version, a.id as id", parameters);
        return result.records().stream().map(r -> {
            var artifact = r.get("id").asString();
            var version = r.get("version").asString();
            return new Dependency(artifact, version);
        }).toList();
    }

    public List<Dependency> getTransitiveDependency(Dependency dependency, Integer level) {
        List<Dependency> result = new ArrayList<>();
        result.add(dependency);
        if (level >= 5) {
            return result;
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("releaseId", dependency.toString());
        var response = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) RETURN d.targetVersion as version, a.id as id", parameters);
        response.records().stream().forEach(r -> {
            var artifact = r.get("id").asString();
            var version = r.get("version").asString();
            var dep = new Dependency(artifact, version);
            result.addAll(getTransitiveDependency(dep, level + 1));
        });
        return result.stream().distinct().toList();
    }

    private List<Dependency> innerGetTransitiveDependency(List<Dependency> dependency) {
        List<Dependency> result = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        result.addAll(dependency);
        for (Dependency dep : dependency) {
            parameters.put("releaseId", dep);
            var response = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) RETURN d.targetVersion as version, a.id as id", parameters);
            var recursiveResponse = response.records().stream().map(r -> {
                var artifact = r.get("id").asString();
                var version = r.get("version").asString();
                return new Dependency(artifact, version);
            });
        }
        return result;

    }


    public static void main(String[] args) {
        String uri = "neo4j://localhost:7687";
        String username = "neo4j";
        String password = "12345678";
        Neo4jConnector neo4jConnector = new Neo4jConnector(uri, username, password);
        RepositoryUtils repositoryProcessor = new RepositoryUtils(neo4jConnector);
        System.out.println(repositoryProcessor.getTransitiveDependency(new Dependency("org.slf4j:slf4j-log4j12","1.7.5"), 0));
//        repositoryProcessor.test();
    }
}

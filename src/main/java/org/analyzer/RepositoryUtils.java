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
        var result = connector.runQuery("MATCH (r:Release {id: $releaseId})-[d:dependency]-(a:Artifact) RETURN d.targetVersion as version, a.id as id", parameters);
        return result.records().stream().map(r -> {
            var artifact = r.get("id").asString();
            var version = r.get("version").asString();
            return new Dependency(artifact, version);
        }).toList();
    }


    public static void main(String[] args) {
        String uri = "neo4j://localhost:7687";
        String username = "neo4j";
        String password = "12345678";
        Neo4jConnector neo4jConnector = new Neo4jConnector(uri, username, password);
        RepositoryUtils repositoryProcessor = new RepositoryUtils(neo4jConnector);
        System.out.println(repositoryProcessor.getDependencyFromReleaseId("com.thinkaurelius.titan:titan-jre6:0.4.3"));
//        repositoryProcessor.test();
    }
}

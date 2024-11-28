package org.analyzer;

import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;

import java.util.Map;
import java.util.Objects;

public class Neo4jConnector {
    private final Driver driver;

    public Neo4jConnector(String uri, String username, String password) {
        try {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            driver.verifyConnectivity();
            System.out.println("Connection established.");
        } catch (Neo4jException e) {
            throw new RuntimeException("Failed to create the driver: " + e.getMessage(), e);
        }
    }

    // Method to run a query without parameters
    public EagerResult runQuery(String query) {
        return driver.executableQuery(query).withConfig(QueryConfig.builder().withDatabase("neo4j").build()).execute();
    }

    // Method to run a query with parameters
    public EagerResult runQuery(String query, Map<String, Object> parameters) {
        return driver.executableQuery(query).withParameters(parameters).withConfig(QueryConfig.builder().withDatabase("neo4j").build()).execute();
    }

    // Close the driver when no longer needed
    public void close() {
        if (driver != null) {
            driver.close();
            System.out.println("Driver closed.");
        }
    }
}
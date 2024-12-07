# Replication package for On the Evolution of Unused Dependencies in Java Project Releases: An Empirical Study
This repository includes the replication package including the source code

## Source code
Our main source code is located in
- src/main/java/org/analyzer directory

## Requirements
- This project requires a Java Development Kit (JDK) version 23 or higher
- Neo4j database with [Goblin: Neo4J Maven Central dependency graph data](https://doi.org/10.5281/zenodo.13734581)

java -jar java-dependency-analyzer-1.0-SNAPSHOT.jar /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/org.terrakube.terraform:terraform-spring-boot-autoconfigure org.terrakube.terraform:terraform-spring-boot-autoconfigure:0.2.0 0.2.0 /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/test-repo neo4j://localhost:7687 neo4j 12345678
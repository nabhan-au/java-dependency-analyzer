# Replication package for On the Evolution of Unused Dependencies in Java Project Releases: An Empirical Study
This repository includes the replication package including the source code

## Source code
Our main source code is located in
- src/main/java/org/analyzer directory

## Requirements
- This project requires a Java Development Kit (JDK) version 23 or higher
- Neo4j database with [Goblin: Neo4J Maven Central dependency graph data](https://doi.org/10.5281/zenodo.13734581) (Data should be updated based on your version)

## Run
you can run our program from top directory of this repository with jar file by \
`java -jar target/java -jar java-dependency-analyzer-1.0-SNAPSHOT.jar <repository directory> <artifact> <git branch name> <output directory> <neo4j database uri> <neo4j database username> <neo4j database password>`

Example: \
`java -jar target/java -jar java-dependency-analyzer-1.0-SNAPSHOT.jar <Your repository directory> org.terrakube.terraform:terraform-spring-boot-autoconfigure:0.8.0 0.8.0 <Your output directory> neo4j://localhost:7687 neo4j 12345678`


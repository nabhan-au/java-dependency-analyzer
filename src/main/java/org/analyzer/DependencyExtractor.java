package org.analyzer;

import org.analyzer.models.Dependency;
import org.analyzer.models.ImportArtifact;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

public class DependencyExtractor {
    public static List<Dependency> getProjectDependencies(String repoPath, ImportArtifact projectArtifact) throws Exception {
        if (isFileExist(repoPath, "pom.xml")) {
            System.out.println("Getting dependencies from maven");
            return MavenDependenciesExtractor.getProjectDependencies(repoPath, projectArtifact.getArtifactId());
        } else {
            System.out.println("Getting dependencies from gradle");
            return GradleDependenciesExtractor.getProjectDependencies(repoPath, projectArtifact);
        }
    }

    public static void getAllProjectDependencies(ImportArtifact projectArtifact) throws Exception {
        MavenDependenciesExtractor.getAllProjectDependencies(projectArtifact);
    }

    public static boolean isFileExist(String repositoryPath, String fileName) throws IOException {
        Path startPath = Paths.get(repositoryPath);

        // Ensure the path is a directory
        if (!Files.isDirectory(startPath)) {
            System.out.println("The provided path is not a directory.");
            return false;
        }

        // PathMatcher for pom.xml
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/" + fileName);

        // Walk the file tree
        final boolean[] found = {false};
        Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matcher.matches(file)) {
                    found[0] = true; // pom.xml found
                    return FileVisitResult.TERMINATE; // Stop searching
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return found[0];
    }

    public static ImportArtifact extractDependency(String dependency) {
        var extractedDependency = Arrays.stream(dependency.split(":")).toArray(String[]::new);
        return new ImportArtifact(extractedDependency[1], extractedDependency[0], extractedDependency[2]);
    }
}

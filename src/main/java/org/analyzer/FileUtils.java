package org.analyzer;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.analyzer.models.Dependency;
import org.analyzer.models.DependencyCsv;
import org.analyzer.models.ImportArtifact;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FileUtils {
    private static final List<String> ARTIFACT_SCOPE = Arrays.asList("compile", "provided");

    public static List<Path> getFileList(String repoPath) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
            paths
                    .filter(path -> matcher.matches(path) && Files.isRegularFile(path))
                    .forEach(pathList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pathList;
    }

    public static List<Path> getJarPathList(String directory) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.jar");
        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(path -> matcher.matches(path) && Files.isRegularFile(path))
                    .forEach(pathList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pathList;
    }

    public static Path getPomPath(String directory) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.pom");
        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(path -> matcher.matches(path) && Files.isRegularFile(path))
                    .forEach(pathList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (pathList.size() != 1) {
            throw new RuntimeException("Pom file has more/lower than 1 " + directory);
        }
        return pathList.getFirst();
    }

    public static List<Dependency> getDependencyListFromFile(String csvFilePath, ImportArtifact projectArtifact) {
        try (FileReader reader = new FileReader(csvFilePath)) {
            CsvToBean<DependencyCsv> csvToBean = new CsvToBeanBuilder<DependencyCsv>(reader).withType(DependencyCsv.class).withIgnoreLeadingWhiteSpace(true).build();
            List<DependencyCsv> dependencies = csvToBean.parse();
            DependencyCsv foundDependency = dependencies.stream().filter(dependencyCsv -> dependencyCsv.artifactId.equals(projectArtifact.getGroupId() + ":" + projectArtifact.getArtifactId())).findFirst().orElse(null);
            if (foundDependency == null) {
                throw new Exception("Cannot find dependencies from csv file: " + projectArtifact);
            }
            return Arrays.stream(foundDependency.dependency).map(d -> {
                var split = d.split(":");
                if (split.length < 4) {
                    return null;
                } else if (ARTIFACT_SCOPE.contains(split[3])) {
                    return new Dependency(split[0] + ":" + split[1], split[2]);
                }
                return null;
            }).toList();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Dependency> listDependencyDirectory(File dir, File baseDir) {
        List<Dependency> result = new ArrayList<>();
        if (dir.isDirectory()) {
            // Check if the directory has no subdirectories
            File[] files = dir.listFiles();
            if (files != null) {
                boolean hasSubdirectories = false;
                for (File file : files) {
                    if (file.isDirectory()) {
                        hasSubdirectories = true;
                        result.addAll(listDependencyDirectory(file, baseDir)); // Recurse into subdirectories
                    }
                }
                if (!hasSubdirectories) {
                    // Print leaf directory
                    var basePath = Paths.get(baseDir.getAbsolutePath());
                    var targetPath = Paths.get(dir.getAbsolutePath());
                    var relativizePath = basePath.relativize(targetPath);
                    var artifactPath = relativizePath.subpath(0, relativizePath.getNameCount() - 1).toString();
                    var version = relativizePath.getName(relativizePath.getNameCount() - 1);

                    int lastIndex = artifactPath.lastIndexOf("/");

                    String updatedPath = artifactPath.substring(0, lastIndex) + ":" + artifactPath.substring(lastIndex + 1);
                    result.add(new Dependency(updatedPath.replace("/", "."), version.toString()));
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        File path = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository/com.github.wkennedy.pubsubly:redis-message-header-plugin:1.0.0/dependencies");
        System.out.println(listDependencyDirectory(path, path).get(0));
    }
}

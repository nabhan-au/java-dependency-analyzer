package org.analyzer;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.analyzer.models.Dependency;
import org.analyzer.models.DependencyCsv;
import org.analyzer.models.ImportArtifact;
import org.analyzer.models.json.Project;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    public static Path getPomPathFromDependencyDir(String directoryPath) {
        File directory = new File(directoryPath);

        if (directory.isDirectory()) {
            // Get all .xml files in the directory
            File[] xmlFiles = directory.listFiles((dir, name) -> name.endsWith(".pom"));

            if (xmlFiles != null && xmlFiles.length == 1) {
              return xmlFiles[0].toPath();
            }
        }
        throw new RuntimeException("XML file not found or has XML file more than 1");
    }

    public static List<Path> getAllOtherPomPathFromRepo(String directory) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/pom.xml");

        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            int baseDepth = Paths.get(directory).getNameCount(); // Get the base directory depth
            paths
                    .filter(path -> matcher.matches(path) // Match "pom.xml" files
                            && Files.isRegularFile(path) // Ensure it's a regular file
                            && path.getNameCount() > baseDepth + 1) // Exclude first-level paths
                    .forEach(pathList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pathList;
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
                } else if (ARTIFACT_SCOPE.contains(split[3].toLowerCase().trim())) {
                    return new Dependency(split[0] + ":" + split[1], split[2]);
                }
                return null;
            }).filter(Objects::nonNull).toList();
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

    public static void writeInputToFile(String jsonFilePath, String projectArtifact, String repoPath, String subPath, String gitBranch) {
        Gson gson = new Gson();
        JsonArray jsonArray = new JsonArray();

        try {
            // Check if the file exists and read its content
            FileReader reader = new FileReader(jsonFilePath);
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonArray()) {
                jsonArray = element.getAsJsonArray(); // Load existing array
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("File not found or empty. Creating a new file.");
        }

        // Create a new JSON object for the new data
        JsonObject newEntry = new JsonObject();
        newEntry.addProperty("projectArtifact", projectArtifact);
        newEntry.addProperty("repoPath", repoPath);
        newEntry.addProperty("subPath", subPath);
        newEntry.addProperty("gitBranch", gitBranch);

        // Add the new entry to the array
        jsonArray.add(newEntry);

        // Write the updated JSON array back to the file
        try (FileWriter writer = new FileWriter(jsonFilePath)) {
            gson.toJson(jsonArray, writer);
            System.out.println("Appended new values to JSON file successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Project> readProjectsFromJson(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Project>>() {}.getType();
            return gson.fromJson(reader, listType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void main(String[] args) {
//        File path = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository/com.github.wkennedy.pubsubly:redis-message-header-plugin:1.0.0/dependencies");
//        System.out.println(listDependencyDirectory(path, path).get(0));
//        var artifact = new ImportArtifact("alibabacloud-config20190108","com.aliyun", "1.0.0");
//        var csvPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/datasets/artifact-dependency-details.csv";
//
//        System.out.println(getDependencyListFromFile(csvPath, artifact));
        System.out.println(FileUtils.readProjectsFromJson("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/save_input.json").get(0).projectArtifact);
    }
}

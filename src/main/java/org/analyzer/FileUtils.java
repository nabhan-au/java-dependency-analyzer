package org.analyzer;

import com.google.gson.*;
import org.analyzer.models.Dependency;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileUtils {
    public static List<Path> getFileList(String repoPath) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
            paths
                    .filter(path -> matcher.matches(path) && Files.isRegularFile(path) && !path.toAbsolutePath().toString().contains("/test/"))
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
}

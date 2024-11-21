package org.analyzer;

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
}

package org.analyzer;

import org.analyzer.models.ImportArtifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.maven.model.Parent;

import static org.analyzer.FileUtils.getAllOtherPomPathFromRepo;
import static org.analyzer.FileUtils.getPomPathFromDependencyDir;
import static org.analyzer.MavenArtifactCrawler.extractRepoUrl;
import static org.analyzer.MavenArtifactCrawler.isArtifactExist;

public class PomUtils {

    public static ImportArtifact getPomFromArtifact(String groupId, String artifactId, String version, String basePath) {
        var pomPath = getPomPathFromDependencyDir(basePath);
        var artifactFile = new File(pomPath.toAbsolutePath().toAbsolutePath().toString());
        if (!artifactFile.isFile()) {
            return null;
        }
        return new ImportArtifact(artifactId, groupId, version, pomPath.toAbsolutePath().toString());
    }

    public static List<ImportArtifact> getPomListFromPath(String dir) {
        var result = new ArrayList<ImportArtifact>();
        var pomPathList = getAllOtherPomPathFromRepo(dir);
        for (Path pom : pomPathList) {
            try {
                // Parse the POM file
                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model = reader.read(new FileReader(pom.toAbsolutePath().toString()));

                // Extract artifactId, groupId, and version
                String groupId = model.getGroupId();
                String artifactId = model.getArtifactId();
                String version = model.getVersion();

                // If groupId or version is null, check the parent element
                if (groupId == null && model.getParent() != null) {
                    groupId = model.getParent().getGroupId();
                }
                if (version == null && model.getParent() != null) {
                    version = model.getParent().getVersion();
                }


                result.add(new ImportArtifact(artifactId, groupId, version, pom.toAbsolutePath().toString()));
                // Print the extracted values
                System.out.println("Group ID: " + groupId);
                System.out.println("Artifact ID: " + artifactId);
                System.out.println("Version: " + version);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static void modifyDependencyVersion(String pomFilePath, ImportArtifact importArtifact) {
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        var newVersion = importArtifact.getVersion();
        try {
            // Read the pom.xml file
            File pomFile = new File(pomFilePath);
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model;
            try (FileReader fileReader = new FileReader(pomFile)) {
                model = reader.read(fileReader);
            }

            // Get the list of dependencies
            List<org.apache.maven.model.Dependency> dependencies = model.getDependencies();

            boolean dependencyFound = false;

            for (org.apache.maven.model.Dependency dependency : dependencies) {
                if (groupId.equals(dependency.getGroupId()) && artifactId.equals(dependency.getArtifactId())) {
                    var currentVersion = dependency.getVersion();
                    if (currentVersion == null) {
                        break;
                    }
                    if (currentVersion.equals(newVersion)) {
                        dependencyFound = true;
                        break;
                    }
                    System.out.println("Current version of " + groupId + ":" + artifactId + " is: " + dependency.getVersion());
                    // Update the version
                    dependency.setVersion(newVersion);
                    System.out.println("Updated version to: " + newVersion);
                    dependencyFound = true;
                    break;
                }
            }

            if (!dependencyFound) {
                System.out.println("Dependency not found in pom.xml");
                return;
            }

            // Write the changes back to the pom.xml file
            MavenXpp3Writer writer = new MavenXpp3Writer();
            try (FileWriter fileWriter = new FileWriter(pomFile)) {
                writer.write(fileWriter, model);
            }

            System.out.println("POM file updated successfully!");

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            System.out.println("Error while modifying the POM file.");
        }
    }

    public static void addRepository(String pomFilePath, String repoId, String repoName, String repoUrl, String repoLayout) {
        try {
            // Read the pom.xml file
            File pomFile = new File(pomFilePath);
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model;
            try (FileReader fileReader = new FileReader(pomFile)) {
                model = reader.read(fileReader);
            }

            // Create a new Repository object
            Repository repository = new Repository();
            repository.setId(repoId);
            repository.setName(repoName);
            repository.setUrl(repoUrl);
            repository.setLayout(repoLayout); // Commonly "default"

            // Check if the repository already exists
            boolean repoExists = model.getRepositories().stream()
                    .anyMatch(repo -> repo.getId().equals(repoId));

            if (repoExists) {
                System.out.println("Repository with ID '" + repoId + "' already exists in pom.xml");
            } else {
                // Add the repository to the model
                model.addRepository(repository);
                System.out.println("Added repository with ID '" + repoId + "' to pom.xml");

                // Write the changes back to the pom.xml file
                MavenXpp3Writer writer = new MavenXpp3Writer();
                try (FileWriter fileWriter = new FileWriter(pomFile)) {
                    writer.write(fileWriter, model);
                }
                System.out.println("POM file updated successfully!");
            }

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            System.out.println("Error while modifying the POM file.");
        }
    }


    public static List<ImportArtifact> getAllDependencies(String pomFilePath) {
        try {
            List<ImportArtifact> result = new ArrayList<>();
            // Parse the pom.xml file
            File pomFile = new File(pomFilePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Handle Maven namespaces
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            // Get all <dependency> elements
            NodeList dependencies = doc.getElementsByTagName("dependency");
            if (dependencies.getLength() == 0) {
                System.out.println("No dependencies found in the POM file.");
                return result;
            }

            System.out.println("Dependencies found in the POM file:");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Node dependency = dependencies.item(i);

                if (dependency.getNodeType() == Node.ELEMENT_NODE) {
                    Element dependencyElement = (Element) dependency;

                    // Extract <groupId>, <artifactId>, and <version>
                    String groupId = getElementText(dependencyElement, "groupId");
                    String artifactId = getElementText(dependencyElement, "artifactId");
                    String version = getElementText(dependencyElement, "version");

                    // Print the dependency details
                    System.out.println("Group ID: " + groupId);
                    System.out.println("Artifact ID: " + artifactId);
                    System.out.println("Version: " + version);
                    System.out.println("----------------------------------");
                    result.add(new ImportArtifact(artifactId, groupId, version));
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error while reading the POM file.");
        }
        return new ArrayList<>();
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    public static void removeDependency(String pomFilePath, String groupId, String artifactId) throws Exception {
        File pomFile = new File(pomFilePath);

        if (!pomFile.exists()) {
            throw new IllegalArgumentException("POM file not found at " + pomFilePath);
        }

        // Step 1: Parse the POM file into a Model object
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        try (FileReader fileReader = new FileReader(pomFile)) {
            model = reader.read(fileReader);
        }
        // Step 2: Remove the dependency from the <dependencies> section
        boolean dependencyRemoved = removeDependencyFromSection(model.getDependencies(), groupId, artifactId, "dependencies");

        // Step 3: Remove the dependency from the <dependencyManagement> section
        if (model.getDependencyManagement() != null) {
            boolean dependencyManagementRemoved = removeDependencyFromSection(
                    model.getDependencyManagement().getDependencies(), groupId, artifactId, "dependencyManagement"
            );
        }

        // Step 4: Write the updated POM file back
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            writer.write(fileWriter, model);
        }

        System.out.println("Updated POM file written to " + pomFilePath);
    }

    private static boolean removeDependencyFromSection(Iterable<Dependency> dependencies, String groupId,
                                                       String artifactId, String sectionName) {
        if (dependencies == null) {
            return false;
        }

        Iterator<Dependency> iterator = dependencies.iterator();
        while (iterator.hasNext()) {
            Dependency dependency = iterator.next();
            if (dependency.getGroupId().equals(groupId) && dependency.getArtifactId().equals(artifactId)) {
                iterator.remove();
                System.out.println("Removed dependency from <" + sectionName + ">: " + groupId + ":" + artifactId);
                return true;
            }
        }
        return false;
    }

    private static ImportArtifact getParentArtifact(String pomFilePath) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pomFilePath));

        // Get the parent information
        Parent parent = model.getParent();
        if (parent != null) {
            String groupId = parent.getGroupId();
            String artifactId = parent.getArtifactId();
            String version = parent.getVersion();

            System.out.println("Parent Group ID: " + groupId);
            System.out.println("Parent Artifact ID: " + artifactId);
            System.out.println("Parent Version: " + version);

            return new ImportArtifact(artifactId, groupId, version);
        } else {
            System.out.println("No parent artifact found in the pom.xml.");
            return null;
        }
    }

    public static String getModifiedPomFile(String pomFilePath) throws Exception {
        var dependencyList = getAllDependencies(pomFilePath);
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        WebDriver driver = new ChromeDriver(options);
        List<String> repoList = new ArrayList<>();
        for (var dependency : dependencyList) {
            List<String> possibleVersion;
            try {
                possibleVersion = ArtifactInstaller.fetchMetadata(dependency);
                var nearestVersion = ArtifactInstaller.findNearest(dependency.getVersion(), possibleVersion);
                System.out.println("Nearest Version: " + nearestVersion);
                dependency.setVersion(nearestVersion);
                if (!isArtifactExist("https://repo.maven.apache.org/maven2", dependency)) {
                    throw new Exception("Artifact is not in default repo");
                };
            } catch (Exception e) {
                e.printStackTrace();
                possibleVersion = MavenArtifactCrawler.getVersionsFromMavenRepo(dependency);
                var nearestVersion = ArtifactInstaller.findNearest(dependency.getVersion(), possibleVersion);
                dependency.setVersion(nearestVersion);
                var jarFileUrl = MavenArtifactCrawler.getJarUrlFromMaven(dependency);
                var repoUrl = extractRepoUrl(dependency, jarFileUrl);
                repoList.add(repoUrl);
                var existingArtifactResult = MavenArtifactCrawler.getExistingVersion(dependency, repoUrl, possibleVersion);
                var additionalRepo = existingArtifactResult.a;
                var version = existingArtifactResult.b;
                if (!Objects.equals(additionalRepo, "")) {
                    repoList.add(additionalRepo);
                }

                dependency.setVersion(version);
            }
            modifyDependencyVersion(pomFilePath, dependency);
            addDependencyManagementDependency(pomFilePath, dependency);
        }

        for (String repo : repoList.stream().distinct().toList()) {
            addRepository(pomFilePath, repo, repo, repo, "default");
        }
        driver.quit();

        return pomFilePath;
    }

    public static void addDependencyManagementDependency(String pomFilePath, ImportArtifact artifact) {
        var artifactId = artifact.getArtifactId();
        var groupId = artifact.getGroupId();
        var version = artifact.getVersion();
        try {
            // Read the pom.xml file
            File pomFile = new File(pomFilePath);
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model;
            try (FileReader fileReader = new FileReader(pomFile)) {
                model = reader.read(fileReader);
            }

            // Create a new Dependency object
            org.apache.maven.model.Dependency dependency = new Dependency();
            dependency.setGroupId(groupId);
            dependency.setArtifactId(artifactId);
            dependency.setVersion(version);

            // Get or create the DependencyManagement section
            DependencyManagement dependencyManagement = model.getDependencyManagement();
            if (dependencyManagement == null) {
                dependencyManagement = new DependencyManagement();
                model.setDependencyManagement(dependencyManagement);
            }

            // Check if the dependency already exists
            boolean dependencyExists = dependencyManagement.getDependencies().stream()
                    .anyMatch(dep -> dep.getGroupId().equals(groupId) && dep.getArtifactId().equals(artifactId));

            if (dependencyExists) {
                System.out.println("Dependency already exists in dependencyManagement");
            } else {
                // Add the dependency to the dependencyManagement section
                dependencyManagement.addDependency(dependency);
                System.out.println("Added dependency to dependencyManagement");

                // Write the changes back to the pom.xml file
                MavenXpp3Writer writer = new MavenXpp3Writer();
                try (FileWriter fileWriter = new FileWriter(pomFile)) {
                    writer.write(fileWriter, model);
                }
                System.out.println("POM file updated successfully!");
            }

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            System.out.println("Error while modifying the POM file.");
        }
    }

    public static List<String> findPomsWithArtifactId(String directoryPath, String targetArtifactId) {
        List<String> matchingPomPaths = new ArrayList<>();
        File rootDir = new File(directoryPath);

        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path: " + directoryPath);
        }

        // Recursively search for pom.xml files
        List<File> pomFiles = findPomFiles(rootDir.getAbsolutePath());

        // Check each pom.xml for the specified artifactId
        for (File pomFile : pomFiles) {
            if (hasArtifactId(pomFile, targetArtifactId)) {
                matchingPomPaths.add(pomFile.getAbsolutePath());
            }
        }

        return matchingPomPaths;
    }

    public static List<File> findPomFiles(String directoryPath) {
        List<File> pomFiles = new ArrayList<>();

        try {
            // Walk through the directory and find all pom.xml files
            Files.walk(Paths.get(directoryPath))
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .forEach(path -> pomFiles.add(path.toFile()));
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
        return pomFiles;
    }

    private static boolean hasArtifactId(File pomFile, String targetArtifactId) {
        try (Reader reader = new FileReader(pomFile)) {
            // Create MavenXpp3Reader to parse the pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);

            // Get the artifactId from the parsed model
            String artifactId = model.getArtifactId();

            return targetArtifactId.equals(artifactId);
        } catch (Exception e) {
            System.err.println("Error reading pom.xml: " + pomFile.getAbsolutePath());
        }
        return false;
    }

    public static List<ImportArtifact> extractDependencyFromError(List<String> output) {
        List<ImportArtifact> importArtifacts = new ArrayList<>();
        for (String line : output) {
            if (line.contains("dependency: ") && line.contains("[ERROR]")) {
                var splitLine = line.split(":");
                var groupId = splitLine[1].trim();
                var artifactId = splitLine[2].trim();
                importArtifacts.add(new ImportArtifact(artifactId, groupId, ""));
            }
        }
        return importArtifacts;
    }
}

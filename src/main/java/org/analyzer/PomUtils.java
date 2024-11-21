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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.analyzer.MavenArtifactCrawler.extractRepoUrl;
import static org.analyzer.MavenArtifactCrawler.isArtifactExist;

public class PomUtils {

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
                var jarFileUrl = MavenArtifactCrawler.getJarUrlFromMaven(dependency);
                var repoUrl = extractRepoUrl(dependency, jarFileUrl);
                repoList.add(repoUrl);
                var nearestVersion = ArtifactInstaller.findNearest(dependency.getVersion(), possibleVersion);
                dependency.setVersion(nearestVersion);
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

    public static void main(String[] args) throws Exception {
        String pomFilePath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository/us.ihmc/ihmc-perception/ihmc-perception-0.14.0-240126.pom";

        getModifiedPomFile(pomFilePath);
//        String targetGroupId = "org.springframework.boot";
//        String targetArtifactId = "spring-boot-starter-web";
//        String newVersion = "2.7.18";
//
//        modifyDependencyVersion(pomFilePath, targetGroupId, targetArtifactId, newVersion);
    }
}

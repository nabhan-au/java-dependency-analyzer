package org.analyzer;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class PomReader {

    public static String getVersionFromPom(String pomFilePath, String groupId, String artifactId) throws Exception {
        File pomFile = new File(pomFilePath);

        if (!pomFile.exists()) {
            throw new IllegalArgumentException("POM file does not exist at: " + pomFilePath);
        }

        // Parse the XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(pomFile);

        // Normalize the document
        document.getDocumentElement().normalize();

        // Get the <dependencies> node
        NodeList dependencies = document.getElementsByTagName("dependency");

        // Iterate through each <dependency>
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependencyNode = dependencies.item(i);

            if (dependencyNode.getNodeType() == Node.ELEMENT_NODE) {
                Element dependencyElement = (Element) dependencyNode;

                String depGroupId = getElementValue(dependencyElement, "groupId");
                String depArtifactId = getElementValue(dependencyElement, "artifactId");
                String depVersion = getElementValue(dependencyElement, "version");

                // Check if groupId and artifactId match
                if (groupId.equals(depGroupId) && artifactId.equals(depArtifactId)) {
                    return depVersion; // Return the version if found
                }
            }
        }

        // Dependency not found
        return null;
    }

    private static String getElementValue(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}

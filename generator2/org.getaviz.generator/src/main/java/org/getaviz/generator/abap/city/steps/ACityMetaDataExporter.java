package org.getaviz.generator.abap.city.steps;

import org.apache.commons.lang3.math.NumberUtils;
import org.getaviz.generator.SettingsConfiguration;
import org.getaviz.generator.abap.enums.SAPNodeProperties;
import org.getaviz.generator.abap.enums.SAPRelationLabels;
import org.getaviz.generator.abap.repository.ACityElement;
import org.getaviz.generator.abap.repository.ACityRepository;
import org.getaviz.generator.abap.repository.SourceNodeRepository;
import org.getaviz.generator.database.DatabaseConnector;
import org.getaviz.run.local.common.Maps;
import org.neo4j.driver.v1.types.Node;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class ACityMetaDataExporter {
    private static SettingsConfiguration config = SettingsConfiguration.getInstance();
    private static DatabaseConnector connector = DatabaseConnector.getInstance(config.getDefaultBoldAddress());
    private SourceNodeRepository nodeRepository;
    private ACityRepository aCityRepository;

    public ACityMetaDataExporter(ACityRepository aCityRepository, SourceNodeRepository sourceNodeRepository) {
        this.nodeRepository = sourceNodeRepository;
        this.aCityRepository = aCityRepository;
    }

    public void exportMetaDataFile() {
        Writer fw = null;
        try {
            File outputDir = new File(config.getOutputMap());
            String path = outputDir.getAbsolutePath() + "/metaData.json";
            fw = new FileWriter(path);
            fw.write(toJSON(aCityRepository.getAllElements()));
        } catch (IOException e) {
            System.out.println(e);
        } finally {
            if (fw != null)
                try {
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    public void setMetaDataPropToACityElements() {
        Collection<ACityElement> aCityElements = aCityRepository.getAllElements();
        for (final ACityElement element : aCityElements) {
            String metaData = toMetaData(element);
            element.setMetaData("{" + metaData + "}");
        }
    }

    private String toJSON(Collection<ACityElement> elements) {
        StringBuilder metaDataFile = new StringBuilder();
        boolean hasElements = false;
        for (final ACityElement element: elements) {
            if (!hasElements) {
                hasElements = true;
                metaDataFile.append("[{");
            } else {
                metaDataFile.append("\n},{");
            }
            metaDataFile.append("\n");
            metaDataFile.append(toMetaData(element));
        }
        if (hasElements) {
            metaDataFile.append("}]");
        }
        return metaDataFile.toString();
    }

    private String toMetaData(ACityElement element) {
        StringBuilder builder = new StringBuilder();

        // Add element hash
        builder.append("\""+ Maps.getMetaDataProperty(SAPNodeProperties.element_id.name()) + "\": \"" + element.getHash() + "\"," +"\n");
        // Add qualifiedName
        builder.append("\"qualifiedName\": \"" + getQualifiedName(element) + "\",\n");
        // Add node information
        builder.append(getNodeMetaInfo(element));
        // Add relations
        builder.append(getRelationsMetaInfo(element));
        // Add additional meta
        builder.append(getAdditionalMetaInfo(element));

        // Make sure we have the right syntax -> no commas at the end
        char lastChar = builder.charAt(builder.length() - 1);
        if (Character.compare(lastChar, '\n') == 0) {
            lastChar = builder.charAt(builder.length() - 2);

            if (Character.compare(lastChar, ',') == 0) {
                builder.deleteCharAt(builder.length() - 1); // Delete '\n'
                builder.deleteCharAt(builder.length() - 1); // Delete ,
            }
        }

        return builder.toString();
    }

    private String getNodeMetaInfo(ACityElement element) {
        StringBuilder builder = new StringBuilder();
        Node node = element.getSourceNode();
        // For some accessory elements there is no source node
        if (node == null) {
            return "";
        }
        Arrays.asList(SAPNodeProperties.values()).forEach(prop -> {
            if (prop == SAPNodeProperties.element_id) {
                return; // already added as first prop by toMetaData()
            }

            // Don't write properties  with NULL value
            if (node.get(prop.toString()).isNull()) {
                return;
            }

            // Remove extra "" (written by Neo4j)
            String propValue = node.get(prop.toString()).toString().replaceAll("\"", "");

            // Belongs to - must be hash a value of a parent container
            if (prop == SAPNodeProperties.container_id) {
                propValue = "";
                if (element.getParentElement() != null) {
                    //element.getParentElement() may be empty, so use relations to find parent
                    propValue = getContainerHash(node);
                }
            }

            // Write strings with quotation marks and numbers without
            if (NumberUtils.isCreatable(propValue)) {
                builder.append("\""+ Maps.getMetaDataProperty(prop.toString()) + "\": " + propValue + "," + "\n");
            } else {
                builder.append("\""+ Maps.getMetaDataProperty(prop.toString()) + "\": \"" + propValue + "\"," + "\n");
            }
        });

        return builder.toString();
    }

    private String getRelationsMetaInfo(ACityElement element) {
        StringBuilder builder = new StringBuilder();
        Node node = element.getSourceNode();
        // For some accessory elements there is no source node
        if (node == null) {
            return "";
        }

        // Add USES and INHERIT relations
        String nodeType = node.get("type").asString();
        if (Maps.getNodesWithUsesRelationByType().contains(nodeType)) {
            builder.append("\"calls\": \"" + getRelations(node, SAPRelationLabels.USES, true) + "\",\n");
            builder.append("\"calledBy\": \"" + getRelations(node, SAPRelationLabels.USES, false) + "\",\n");
        }
        if (Maps.getNodesWithInheritRelationByType().contains(nodeType)) {
            builder.append("\"subClassOf\": \"" + getRelations(node, SAPRelationLabels.INHERIT, true) + "\",\n");
            builder.append("\"superClassOf\": \"" + getRelations(node, SAPRelationLabels.INHERIT, false) + "\",\n");
        }

        return builder.toString();
    }

    private String getAdditionalMetaInfo(ACityElement element) {
        StringBuilder builder = new StringBuilder();
        Node node = element.getSourceNode();
        String nodeType = node.get("type").asString();

        //signature for methods
        if (node.get("type").asString().equals("METH")) {
            builder.append("\"signature\": \"" + "" + "\",\n");
        }

        return builder.toString();
    }

    private String getQualifiedName(ACityElement element) {
        return "";
    }

    private String getContainerHash(Node node) {
        Collection<Node> parentNodes = nodeRepository.getRelatedNodes(node, SAPRelationLabels.CONTAINS, false);
        if (parentNodes.isEmpty()) {
            return "";
        }

        // Make sure we get direct parent
        String container_id = node.get(SAPNodeProperties.container_id.name()).asString();
        for (Node parentNode: parentNodes) {
            String element_id = parentNode.get(SAPNodeProperties.element_id.name()).asString();
            if (element_id.equals(container_id)) {
                ACityElement parentElement = aCityRepository.getElementBySourceID(parentNode.id());
                // Some SAP standard packages may not included
                if (parentElement == null) {
                    return "";
                }
                return parentElement.getHash();
            }
        }

        // If no hash was found, for example default SAP packages, no container_id will be written.
        return "";
    }

    private String getRelations(Node node, SAPRelationLabels label, Boolean direction) {
        Collection<Node> nodes = nodeRepository.getRelatedNodes(node, label, direction);
        if (nodes.isEmpty()) {
            return "";
        }

        List<String> nodesHashes = getNodesHashes(nodes);
        return String.join(", ", nodesHashes); //returns "hash, hash_2, hash*"
    }

    private List<String> getNodesHashes(Collection<Node> nodes) {
        List<String> nodesHashes = new ArrayList<>();
        for (Node node : nodes) {
            Long nodeId = node.id();
            ACityElement aCityElement = aCityRepository.getElementBySourceID(nodeId);
            if (aCityElement == null) {
                continue;
            }

            nodesHashes.add(aCityElement.getHash());
        }
        return nodesHashes;
    }
}

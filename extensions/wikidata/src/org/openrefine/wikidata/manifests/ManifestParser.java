package org.openrefine.wikidata.manifests;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ManifestParser {

    private static final Logger logger = LoggerFactory.getLogger(ManifestParser.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Manifest parse(String manifestJson) throws ManifestException {
        JsonNode root;
        try {
            root = mapper.readTree(manifestJson);
        } catch (IOException e) {
            throw new ManifestException("invalid manifest format", e);
        }

        String version = root.path("version").textValue();
        if (StringUtils.isBlank(version)) {
            throw new ManifestException("invalid manifest format, version is missing");
        }
        if (!version.matches("[0-9]+\\.[0-9]+")) {
            throw new ManifestException("invalid version: " + version);
        }

        String majorVersion = version.split("\\.")[0];
        // support only v1.x for now
        if ("1".equals(majorVersion)) {
            return new ManifestV1(root);
        } else {
            throw new ManifestException("unsupported manifest version: " + version);
        }
    }
}

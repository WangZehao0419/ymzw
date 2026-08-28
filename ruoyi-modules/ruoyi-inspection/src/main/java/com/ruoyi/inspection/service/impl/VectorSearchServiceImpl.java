package com.ruoyi.inspection.service.impl;

import com.ruoyi.inspection.service.VectorSearchService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chroma vector store lookup service.
 */
@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final Pattern PART_TYPE_PATTERN = Pattern.compile("\\(([A-Z_]+)\\)");

    private static final Set<String> PART_TYPE_CODES = Set.of(
            "TITANIUM", "STEEL", "ALUMINUM", "COPPER", "COMPOSITE",
            "HIGH_TEMP_ALLOY", "MAGNESIUM", "PLASTIC", "CERAMIC", "TUNGSTEN"
    );

    private static final Map<String, String> PART_TYPE_ALIASES = new LinkedHashMap<>();

    private static final long CACHE_TTL_MILLIS = 60_000L;

    static {
        registerAliases("TITANIUM", "TITANIUM", "\u949b", "\u949b\u5408\u91d1");
        registerAliases("STEEL", "STEEL", "\u94a2", "\u4e0d\u9508\u94a2");
        registerAliases("ALUMINUM", "ALUMINUM", "ALUMINIUM", "\u94dd", "\u94dd\u5408\u91d1");
        registerAliases("COPPER", "COPPER", "\u94dc", "\u94dc\u5408\u91d1");
        registerAliases("COMPOSITE", "COMPOSITE", "\u590d\u5408", "\u590d\u5408\u6750\u6599");
        registerAliases("HIGH_TEMP_ALLOY", "HIGH_TEMP_ALLOY", "SUPERALLOY", "\u9ad8\u6e29\u5408\u91d1");
        registerAliases("MAGNESIUM", "MAGNESIUM", "\u9541", "\u9541\u5408\u91d1");
        registerAliases("PLASTIC", "PLASTIC", "\u5851\u6599", "\u5de5\u7a0b\u5851\u6599");
        registerAliases("CERAMIC", "CERAMIC", "\u9676\u74f7");
        registerAliases("TUNGSTEN", "TUNGSTEN", "\u94a8", "\u94a8\u5408\u91d1");
    }

    @Value("${chroma.host:http://106.53.26.28}")
    private String chromaHost;

    @Value("${chroma.port:8000}")
    private int chromaPort;

    @Value("${chroma.collection-name:ai_knowledge_base}")
    private String collectionName;

    @Value("${chroma.tenant:default_tenant}")
    private String tenant;

    @Value("${chroma.database:default_database}")
    private String database;

    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String collectionId;
    private volatile long cacheTimeMillis = 0L;
    private volatile Set<String> availablePartTypes = Collections.emptySet();

    @PostConstruct
    public void init() {
        refreshCacheIfNeeded();
    }

    @Override
    public boolean existsInspectionMethodology(String partType) {
        String normalized = normalizePartType(partType);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }

        Set<String> snapshot = refreshCacheIfNeeded();
        if (snapshot.isEmpty()) {
            return false;
        }

        return snapshot.contains(normalized);
    }

    private Set<String> refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        Set<String> current = availablePartTypes;
        if (!current.isEmpty() && (now - cacheTimeMillis) < CACHE_TTL_MILLIS) {
            return current;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            current = availablePartTypes;
            if (!current.isEmpty() && (now - cacheTimeMillis) < CACHE_TTL_MILLIS) {
                return current;
            }

            Set<String> refreshed = fetchAvailablePartTypes();
            availablePartTypes = refreshed;
            cacheTimeMillis = now;
            return refreshed;
        }
    }

    private Set<String> fetchAvailablePartTypes() {
        String id = getCollectionId();
        if (!StringUtils.hasText(id)) {
            log.warn("Chroma collection id is empty: {}", collectionName);
            return Collections.emptySet();
        }

        String url = buildApiV2Base() + "/collections/" + id + "/get";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", null);
        body.put("where", null);
        body.put("limit", 1000);
        body.put("offset", null);
        body.put("where_document", null);
        body.put("include", Arrays.asList("metadatas", "documents"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Chroma get failed, status={}", response.getStatusCode());
                return Collections.emptySet();
            }

            Set<String> detected = new HashSet<>();
            parseTypesFromMetadatas(response.getBody().get("metadatas"), detected);
            parseTypesFromDocuments(response.getBody().get("documents"), detected);

            if (detected.isEmpty()) {
                log.warn("No part type detected from vector collection {}", collectionName);
                return Collections.emptySet();
            }

            log.info("Detected part types from vector store: {}", detected);
            return Collections.unmodifiableSet(detected);
        } catch (Exception e) {
            log.warn("Fetch vector blocks failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private void parseTypesFromMetadatas(Object metadatasObject, Set<String> result) {
        if (!(metadatasObject instanceof List<?> metadatas)) {
            return;
        }

        for (Object metadataObj : metadatas) {
            if (!(metadataObj instanceof Map<?, ?> metadataMap)) {
                continue;
            }
            detectTypesFromText(asText(metadataMap.get("filename")), result);
            detectTypesFromText(asText(metadataMap.get("source")), result);
            detectTypesFromText(asText(metadataMap.get("category")), result);
        }
    }

    private void parseTypesFromDocuments(Object documentsObject, Set<String> result) {
        if (!(documentsObject instanceof List<?> documents)) {
            return;
        }

        for (Object documentObj : documents) {
            detectTypesFromText(asText(documentObj), result);
        }
    }

    private void detectTypesFromText(String text, Set<String> result) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        String upper = text.toUpperCase(Locale.ROOT);
        Matcher matcher = PART_TYPE_PATTERN.matcher(upper);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (PART_TYPE_CODES.contains(code)) {
                result.add(code);
            }
        }

        for (String code : PART_TYPE_CODES) {
            if (upper.contains(code)) {
                result.add(code);
            }
        }
    }

    private String getCollectionId() {
        if (StringUtils.hasText(collectionId)) {
            return collectionId;
        }
        synchronized (this) {
            if (!StringUtils.hasText(collectionId)) {
                collectionId = fetchCollectionId();
            }
            return collectionId;
        }
    }

    private String fetchCollectionId() {
        String url = buildApiV2Base() + "/collections/" + collectionName;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                return id == null ? null : String.valueOf(id);
            }
        } catch (Exception e) {
            log.warn("Fetch Chroma collection id failed: {}", e.getMessage());
        }
        return null;
    }

    private String buildApiV2Base() {
        return chromaHost + ":" + chromaPort
                + "/api/v2/tenants/" + tenant
                + "/databases/" + database;
    }

    private String normalizePartType(String partType) {
        if (!StringUtils.hasText(partType)) {
            return "";
        }

        String trimmed = partType.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        if (PART_TYPE_CODES.contains(upper)) {
            return upper;
        }

        for (Map.Entry<String, String> entry : PART_TYPE_ALIASES.entrySet()) {
            String alias = entry.getKey();
            if (upper.contains(alias.toUpperCase(Locale.ROOT)) || trimmed.contains(alias)) {
                return entry.getValue();
            }
        }

        return upper;
    }

    private static void registerAliases(String code, String... aliases) {
        for (String alias : aliases) {
            PART_TYPE_ALIASES.put(alias, code);
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

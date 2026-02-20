package com.knowledgeos.memory;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * HTTP client for Qdrant vector database.
 *
 * Stores memory entries as 1536-dim vectors (OpenAI text-embedding-ada-002 size).
 * In the absence of an embedding API, this implementation uses a placeholder
 * zero vector for storage and falls back to DB-only search when Qdrant is unavailable.
 *
 * Production: replace generatePlaceholderVector() with a real embedding API call.
 */
@Singleton
public class QdrantMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantMemoryStore.class);

    @Value("${qdrant.host:localhost}")
    String qdrantHost;

    @Value("${qdrant.port:6333}")
    int qdrantPort;

    @Value("${qdrant.collection:knowledgeos-memory}")
    String collection;

    @Value("${qdrant.vector-size:1536}")
    int vectorSize;

    private HttpClient createClient() {
        try {
            return HttpClient.create(new URL(String.format("http://%s:%d", qdrantHost, qdrantPort)));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Qdrant URL", e);
        }
    }

    /**
     * Upsert a memory entry as a vector point in Qdrant.
     *
     * @param qdrantId   stable UUID used as the Qdrant point ID
     * @param projectId  for payload filter during search
     * @param layer      canonical | feature | scratch — stored in payload
     * @param text       content to embed (title + content concatenated)
     * @param payload    additional metadata stored with the vector
     */
    public void upsert(UUID qdrantId, UUID projectId, String layer, String text, Map<String, Object> payload) {
        try {
            ensureCollection();

            Map<String, Object> fullPayload = new HashMap<>(payload);
            fullPayload.put("projectId", projectId.toString());
            fullPayload.put("layer", layer);

            float[] vector = generatePlaceholderVector(text);

            // Build Qdrant upsert payload
            Map<String, Object> point = Map.of(
                "id", qdrantId.toString(),
                "vector", vectorToList(vector),
                "payload", fullPayload
            );
            Map<String, Object> body = Map.of("points", List.of(point));

            String url = String.format("http://%s:%d/collections/%s/points", qdrantHost, qdrantPort, collection);
            createClient().toBlocking().exchange(
                HttpRequest.PUT(URI.create(url), body).contentType(MediaType.APPLICATION_JSON_TYPE)
            );
            log.debug("Upserted point {} to Qdrant collection {}", qdrantId, collection);

        } catch (Exception e) {
            log.warn("Qdrant upsert failed for point {} — search will fall back to DB: {}", qdrantId, e.getMessage());
        }
    }

    /**
     * Semantic search: returns Qdrant point IDs ordered by similarity to the query.
     *
     * @param projectId  filter results to this project
     * @param layer      if non-null, filter to this layer
     * @param query      search query text
     * @param limit      max results
     * @return ordered list of Qdrant point UUIDs (most similar first)
     */
    public List<UUID> search(UUID projectId, String layer, String query, int limit) {
        try {
            ensureCollection();

            float[] queryVector = generatePlaceholderVector(query);

            // Build filter
            List<Map<String, Object>> mustConditions = new ArrayList<>();
            mustConditions.add(Map.of("key", "projectId", "match", Map.of("value", projectId.toString())));
            if (layer != null) {
                mustConditions.add(Map.of("key", "layer", "match", Map.of("value", layer)));
            }

            Map<String, Object> body = Map.of(
                "vector", vectorToList(queryVector),
                "limit", limit,
                "with_payload", false,
                "filter", Map.of("must", mustConditions)
            );

            String url = String.format("http://%s:%d/collections/%s/points/search", qdrantHost, qdrantPort, collection);
            Map<?, ?> response = createClient().toBlocking().retrieve(
                HttpRequest.POST(URI.create(url), body).contentType(MediaType.APPLICATION_JSON_TYPE),
                Map.class
            );

            List<?> results = (List<?>) response.get("result");
            if (results == null) return List.of();

            return results.stream()
                .map(r -> {
                    Map<?, ?> result = (Map<?, ?>) r;
                    return UUID.fromString(result.get("id").toString());
                })
                .toList();

        } catch (Exception e) {
            log.warn("Qdrant search failed — returning empty results: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Delete a point from Qdrant when a memory entry is deleted.
     */
    public void delete(UUID qdrantId) {
        try {
            String url = String.format("http://%s:%d/collections/%s/points/delete", qdrantHost, qdrantPort, collection);
            Map<String, Object> body = Map.of("points", List.of(qdrantId.toString()));
            createClient().toBlocking().exchange(
                HttpRequest.POST(URI.create(url), body).contentType(MediaType.APPLICATION_JSON_TYPE)
            );
            log.debug("Deleted point {} from Qdrant", qdrantId);
        } catch (Exception e) {
            log.warn("Qdrant delete failed for point {}: {}", qdrantId, e.getMessage());
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void ensureCollection() {
        try {
            String url = String.format("http://%s:%d/collections/%s", qdrantHost, qdrantPort, collection);
            try {
                createClient().toBlocking().exchange(HttpRequest.GET(url));
            } catch (HttpClientException e) {
                // Collection doesn't exist — create it
                Map<String, Object> body = Map.of(
                    "vectors", Map.of("size", vectorSize, "distance", "Cosine")
                );
                createClient().toBlocking().exchange(
                    HttpRequest.PUT(URI.create(url), body).contentType(MediaType.APPLICATION_JSON_TYPE)
                );
                log.info("Created Qdrant collection: {}", collection);
            }
        } catch (Exception e) {
            log.warn("Cannot reach Qdrant — operating without semantic search: {}", e.getMessage());
        }
    }

    /**
     * Placeholder: returns a deterministic pseudo-vector based on text length.
     * Replace with a real embedding API call (e.g. Claude / OpenAI embeddings) in production.
     */
    float[] generatePlaceholderVector(String text) {
        float[] v = new float[vectorSize];
        // Use text hashCode to distribute values (not real semantic embedding)
        int hash = text != null ? text.hashCode() : 0;
        for (int i = 0; i < vectorSize; i++) {
            v[i] = (float) Math.sin(hash * (i + 1) * 0.001);
        }
        return v;
    }

    private List<Float> vectorToList(float[] v) {
        List<Float> list = new ArrayList<>(v.length);
        for (float f : v) list.add(f);
        return list;
    }
}

package tidebound.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tidebound.database.DotaConstantsDatabaseConfig;

@Service
public class DotaConstantsIngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DotaConstantsIngestionService.class);
    private static final String REPOSITORY_BASE = "https://api.github.com/repos/odota/dotaconstants/contents/";
    private static final String SCHEMA = "dota_constants";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DotaConstantsIngestionService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Integer> syncConstants() {
        Map<String, Integer> results = new LinkedHashMap<>();

        for (String directory : List.of("build", "json")) {
            List<RemoteJsonFile> files = listJsonFiles(directory);
            files.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
            for (RemoteJsonFile file : files) {
                String tableName = toTableName(directory, file.name());
                String qualifiedTableName = qualifyTableName(tableName);
                try {
                    JsonNode payload = downloadJson(file.downloadUrl());
                    int insertedRows = persist(qualifiedTableName, payload);
                    results.put(qualifiedTableName, insertedRows);
                    LOGGER.info("Loaded {} rows into table {}", insertedRows, qualifiedTableName);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while downloading JSON: " + file.downloadUrl(), exception);
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to download JSON from " + file.downloadUrl(), exception);
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to persist JSON into table " + tableName, exception);
                }
            }
        }

        return results;
    }

    private List<RemoteJsonFile> listJsonFiles(String directory) {
        String url = REPOSITORY_BASE + directory;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "tidebound-parser")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("GitHub API responded with status " + response.statusCode() + " for " + url);
            }

            JsonNode body = objectMapper.readTree(response.body());
            List<RemoteJsonFile> files = new ArrayList<>();

            for (JsonNode node : body) {
                String type = node.path("type").asText();
                String name = node.path("name").asText();
                String downloadUrl = node.path("download_url").asText(null);

                if ("file".equals(type) && name.endsWith(".json") && downloadUrl != null) {
                    files.add(new RemoteJsonFile(name, downloadUrl));
                }
            }

            return files;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while listing JSON files for directory " + directory, exception);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to list JSON files for directory " + directory, exception);
        }
    }

    private JsonNode downloadJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "tidebound-parser")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Failed to download JSON from " + url + ". Status: " + response.statusCode());
            }

            try {
                return objectMapper.readTree(response.body());
            } catch (JsonProcessingException exception) {
                throw new RuntimeException("Invalid JSON received from " + url, exception);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while downloading JSON from " + url, exception);
        }
    }

    private int persist(String qualifiedTableName, JsonNode payload) throws SQLException, JsonProcessingException {
        try (Connection connection = DotaConstantsDatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureTableExists(connection, qualifiedTableName);
                clearTable(connection, qualifiedTableName);

                int rows;
                if (payload.isObject()) {
                    rows = insertObjectEntries(connection, qualifiedTableName, payload);
                } else if (payload.isArray()) {
                    rows = insertArrayEntries(connection, qualifiedTableName, payload);
                } else {
                    rows = insertScalarEntry(connection, qualifiedTableName, payload);
                }

                connection.commit();
                return rows;
            } catch (SQLException | JsonProcessingException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    private void ensureTableExists(Connection connection, String qualifiedTableName) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + qualifiedTableName + " ("
                + "entry_key TEXT PRIMARY KEY,"
                + "data JSONB NOT NULL"
                + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void clearTable(Connection connection, String qualifiedTableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + qualifiedTableName);
        }
    }

    private int insertObjectEntries(Connection connection, String qualifiedTableName, JsonNode payload)
            throws SQLException, JsonProcessingException {
        int rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + qualifiedTableName + " (entry_key, data) VALUES (?, ?) "
                        + "ON CONFLICT (entry_key) DO UPDATE SET data = EXCLUDED.data")) {
            for (var iterator = payload.fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                rows += addRow(statement, entry.getKey(), entry.getValue());
            }
        }
        return rows;
    }

    private int insertArrayEntries(Connection connection, String qualifiedTableName, JsonNode payload)
            throws SQLException, JsonProcessingException {
        int rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + qualifiedTableName + " (entry_key, data) VALUES (?, ?) "
                        + "ON CONFLICT (entry_key) DO UPDATE SET data = EXCLUDED.data")) {
            for (int index = 0; index < payload.size(); index++) {
                rows += addRow(statement, String.valueOf(index), payload.get(index));
            }
        }
        return rows;
    }

    private int insertScalarEntry(Connection connection, String qualifiedTableName, JsonNode payload)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + qualifiedTableName + " (entry_key, data) VALUES (?, ?) "
                        + "ON CONFLICT (entry_key) DO UPDATE SET data = EXCLUDED.data")) {
            return addRow(statement, "value", payload);
        }
    }

    private int addRow(PreparedStatement statement, String key, JsonNode value)
            throws SQLException, JsonProcessingException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(objectMapper.writeValueAsString(value));

        statement.setString(1, key);
        statement.setObject(2, jsonObject);
        return statement.executeUpdate();
    }

    private String toTableName(String directory, String fileName) {
        String baseName = fileName.replace(".json", "");
        String rawTable = directory + "_" + baseName;
        String sanitized = rawTable
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        return sanitized.replaceAll("__+", "_");
    }

    private String qualifyTableName(String tableName) {
        return SCHEMA + "." + tableName;
    }

    private record RemoteJsonFile(String name, String downloadUrl) {}
}



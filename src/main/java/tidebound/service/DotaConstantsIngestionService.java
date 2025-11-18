package tidebound.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.core.io.ClassPathResource;
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

        // Populate hero Chinese names table
        try {
            int heroNamesCount = populateHeroChineseNames();
            results.put("hero_chinese_names", heroNamesCount);
            LOGGER.info("Populated {} hero Chinese names", heroNamesCount);
        } catch (Exception exception) {
            LOGGER.error("Failed to populate hero Chinese names", exception);
            results.put("hero_chinese_names", -1);
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

    /**
     * Populates the hero_chinese_names table with hero ID to Chinese name mappings.
     * Tries multiple approaches: database, repository files, and Steam API.
     */
    private int populateHeroChineseNames() throws IOException, InterruptedException, SQLException {
        // First try to use already-synced data from the database
        int result = populateHeroChineseNamesFromDatabase();
        if (result > 0) {
            LOGGER.info("Successfully populated {} hero Chinese names from database", result);
            return result;
        }
        
        // Try to find and download heroes file from repository
        LOGGER.info("No heroes data found in database, attempting to download from repository");
        try {
            // Check both json and build directories
            for (String directory : List.of("json", "build")) {
                List<RemoteJsonFile> files = listJsonFiles(directory);
                // Look for files that might contain hero data
                RemoteJsonFile heroesFile = files.stream()
                        .filter(file -> {
                            String name = file.name().toLowerCase();
                            return name.equals("heroes.json") || 
                                   name.startsWith("hero") && name.endsWith(".json");
                        })
                        .findFirst()
                        .orElse(null);
                
                if (heroesFile != null) {
                    LOGGER.info("Found heroes file: {}", heroesFile.name());
                    JsonNode heroesData = downloadJson(heroesFile.downloadUrl());
                    result = populateHeroChineseNamesFromJson(heroesData);
                    if (result > 0) {
                        return result;
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to download heroes.json from repository", exception);
        }
        
        // Try to find localized name files in repository
        LOGGER.info("Searching for localized name files in repository");
        try {
            result = populateHeroChineseNamesFromLocalizedFiles();
            if (result > 0) {
                LOGGER.info("Successfully populated {} hero Chinese names from localized files", result);
                return result;
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to fetch hero Chinese names from localized files", exception);
        }
        
        // Try OpenDota API as fallback
        LOGGER.info("Attempting to fetch hero Chinese names from OpenDota API");
        try {
            result = populateHeroChineseNamesFromOpenDota();
            if (result > 0) {
                LOGGER.info("Successfully populated {} hero Chinese names from OpenDota API", result);
                return result;
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to fetch hero Chinese names from OpenDota API", exception);
        }
        
        // Final fallback: use hardcoded mapping from resource file
        LOGGER.info("Using hero Chinese names mapping from resource file as final fallback");
        try {
            result = populateHeroChineseNamesFromHardcodedMapping();
            if (result > 0) {
                LOGGER.info("Successfully populated {} hero Chinese names from resource file", result);
                return result;
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to populate hero Chinese names from resource file", exception);
        }
        
        LOGGER.warn("Could not populate hero Chinese names from any source");
        return 0;
    }

    /**
     * Populates hero_chinese_names from heroes.json JSON data.
     */
    private int populateHeroChineseNamesFromJson(JsonNode heroesData) throws SQLException {
        try (Connection connection = DotaConstantsDatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int rows = 0;
                String sql = "INSERT INTO " + SCHEMA + ".hero_chinese_names (hero_id, chinese_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (hero_id) DO UPDATE SET chinese_name = EXCLUDED.chinese_name, updated_at = CURRENT_TIMESTAMP";
                
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (heroesData.isObject()) {
                        // Heroes data is typically an object where keys are hero IDs
                        for (var iterator = heroesData.fields(); iterator.hasNext(); ) {
                            Map.Entry<String, JsonNode> entry = iterator.next();
                            try {
                                int heroId = Integer.parseInt(entry.getKey());
                                JsonNode heroData = entry.getValue();
                                
                                // Try different possible field names for Chinese name
                                String chineseName = extractChineseName(heroData);
                                
                                if (chineseName != null && !chineseName.isEmpty()) {
                                    statement.setInt(1, heroId);
                                    statement.setString(2, chineseName);
                                    statement.executeUpdate();
                                    rows++;
                                }
                            } catch (NumberFormatException e) {
                                LOGGER.debug("Skipping non-numeric hero key: {}", entry.getKey());
                            }
                        }
                    } else if (heroesData.isArray()) {
                        // Heroes data might be an array
                        for (JsonNode heroNode : heroesData) {
                            JsonNode idNode = heroNode.path("id");
                            if (idNode.isInt()) {
                                int heroId = idNode.asInt();
                                String chineseName = extractChineseName(heroNode);
                                
                                if (chineseName != null && !chineseName.isEmpty()) {
                                    statement.setInt(1, heroId);
                                    statement.setString(2, chineseName);
                                    statement.executeUpdate();
                                    rows++;
                                }
                            }
                        }
                    }
                }
                
                connection.commit();
                return rows;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    /**
     * Attempts to extract Chinese name from hero JSON node.
     * Checks multiple possible field names for Chinese localization.
     */
    private String extractChineseName(JsonNode heroNode) {
        // Try various possible field names for Chinese name
        String[] possibleFields = {
            "localized_name_zh", "name_zh", "localized_name_zh_cn", "name_zh_cn",
            "localized_name_zh-Hans", "name_zh-Hans", "localized_name_zh-Hant", "name_zh-Hant",
            "zh", "zh_cn", "zh-Hans", "zh-Hant"
        };
        
        for (String field : possibleFields) {
            JsonNode node = heroNode.path(field);
            if (node.isTextual() && !node.asText().isEmpty()) {
                return node.asText();
            }
        }
        
        // If no Chinese-specific field found, check if there's a localized_name and try to get from a language object
        JsonNode localizedNameNode = heroNode.path("localized_name");
        if (localizedNameNode.isObject()) {
            // Try Chinese language codes in the localized_name object
            for (String langCode : new String[]{"zh", "zh_cn", "zh-CN", "zh-Hans", "zh-Hant"}) {
                JsonNode langNode = localizedNameNode.path(langCode);
                if (langNode.isTextual() && !langNode.asText().isEmpty()) {
                    return langNode.asText();
                }
            }
        }
        
        LOGGER.debug("No Chinese name found for hero node: {}", heroNode);
        return null;
    }

    /**
     * Attempts to populate hero_chinese_names from already synced data in dota_constants schema.
     * This is the preferred method as it uses data that's already been synced.
     * Checks multiple possible table names for hero data.
     */
    private int populateHeroChineseNamesFromDatabase() throws SQLException {
        try (Connection connection = DotaConstantsDatabaseConfig.getConnection()) {
            // Try multiple possible table names
            String[] possibleTableNames = {
                "json_heroes",
                "build_heroes",
                "json_hero",
                "build_hero"
            };
            
            String heroesTableName = null;
            // Find which table exists
            try (Statement checkStmt = connection.createStatement()) {
                for (String tableName : possibleTableNames) {
                    var resultSet = checkStmt.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '" + SCHEMA + "' AND table_name = '" + tableName + "')"
                    );
                    if (resultSet.next() && resultSet.getBoolean(1)) {
                        heroesTableName = SCHEMA + "." + tableName;
                        LOGGER.debug("Found heroes table: {}", heroesTableName);
                        break;
                    }
                }
            }
            
            if (heroesTableName == null) {
                // Try to find any table with "hero" in the name
                try (Statement checkStmt = connection.createStatement()) {
                    var resultSet = checkStmt.executeQuery(
                        "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = '" + SCHEMA + "' AND table_name LIKE '%hero%' " +
                        "LIMIT 1"
                    );
                    if (resultSet.next()) {
                        heroesTableName = SCHEMA + "." + resultSet.getString("table_name");
                        LOGGER.debug("Found heroes table by pattern: {}", heroesTableName);
                    }
                }
            }
            
            if (heroesTableName == null) {
                LOGGER.debug("No heroes table found in dota_constants schema");
                return 0;
            }
            
            // Read all hero entries
            connection.setAutoCommit(false);
            try {
                int rows = 0;
                String insertSql = "INSERT INTO " + SCHEMA + ".hero_chinese_names (hero_id, chinese_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (hero_id) DO UPDATE SET chinese_name = EXCLUDED.chinese_name, updated_at = CURRENT_TIMESTAMP";
                
                try (PreparedStatement selectStmt = connection.prepareStatement("SELECT entry_key, data FROM " + heroesTableName);
                     PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    
                    var resultSet = selectStmt.executeQuery();
                    while (resultSet.next()) {
                        try {
                            int heroId = Integer.parseInt(resultSet.getString("entry_key"));
                            String jsonData = resultSet.getString("data");
                            JsonNode heroNode = objectMapper.readTree(jsonData);
                            
                            String chineseName = extractChineseName(heroNode);
                            if (chineseName != null && !chineseName.isEmpty()) {
                                insertStmt.setInt(1, heroId);
                                insertStmt.setString(2, chineseName);
                                insertStmt.executeUpdate();
                                rows++;
                            }
                        } catch (NumberFormatException | JsonProcessingException e) {
                            LOGGER.debug("Skipping invalid hero entry: {}", e.getMessage());
                        }
                    }
                }
                
                connection.commit();
                return rows;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    /**
     * Attempts to find and use localized name files from the repository.
     * Looks for files like heroes_chinese.json, heroes_zh.json, or similar.
     */
    private int populateHeroChineseNamesFromLocalizedFiles() throws IOException, InterruptedException, SQLException {
        // Check both json and build directories for localized name files
        for (String directory : List.of("json", "build")) {
            try {
                List<RemoteJsonFile> files = listJsonFiles(directory);
                // Look for files that might contain Chinese/localized hero names
                for (RemoteJsonFile file : files) {
                    String name = file.name().toLowerCase();
                    if ((name.contains("hero") && (name.contains("chinese") || name.contains("zh") || 
                         name.contains("localized") || name.contains("lang"))) || 
                        name.equals("heroes_chinese.json") || name.equals("heroes_zh.json") ||
                        name.equals("heroes_zh_cn.json")) {
                        LOGGER.info("Found potential localized heroes file: {}", file.name());
                        JsonNode data = downloadJson(file.downloadUrl());
                        int result = populateHeroChineseNamesFromJson(data);
                        if (result > 0) {
                            return result;
                        }
                    }
                }
            } catch (Exception exception) {
                LOGGER.debug("Error checking {} directory for localized files", directory, exception);
            }
        }
        return 0;
    }
    
    /**
     * Populates hero_chinese_names using OpenDota API.
     * OpenDota provides hero data including localized names.
     */
    private int populateHeroChineseNamesFromOpenDota() throws IOException, InterruptedException, SQLException {
        // OpenDota API endpoint for heroes
        String openDotaUrl = "https://api.opendota.com/api/heroes";
        
        try {
            JsonNode heroes = downloadJson(openDotaUrl);
            
            if (!heroes.isArray()) {
                LOGGER.warn("OpenDota API response does not contain heroes array");
                return 0;
            }
            
            return populateHeroChineseNamesFromOpenDotaResponse(heroes);
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch heroes from OpenDota API", exception);
            throw exception;
        }
    }
    
    /**
     * Populates hero_chinese_names from OpenDota API response.
     * OpenDota returns heroes with localized_name field that may contain Chinese names.
     */
    private int populateHeroChineseNamesFromOpenDotaResponse(JsonNode heroes) throws SQLException {
        try (Connection connection = DotaConstantsDatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int rows = 0;
                String sql = "INSERT INTO " + SCHEMA + ".hero_chinese_names (hero_id, chinese_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (hero_id) DO UPDATE SET chinese_name = EXCLUDED.chinese_name, updated_at = CURRENT_TIMESTAMP";
                
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (JsonNode heroNode : heroes) {
                        JsonNode idNode = heroNode.path("id");
                        
                        if (idNode.isInt()) {
                            int heroId = idNode.asInt();
                            // Try to extract Chinese name using the same method
                            String chineseName = extractChineseName(heroNode);
                            
                            if (chineseName != null && !chineseName.isEmpty()) {
                                statement.setInt(1, heroId);
                                statement.setString(2, chineseName);
                                statement.executeUpdate();
                                rows++;
                            }
                        }
                    }
                }
                
                connection.commit();
                return rows;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    /**
     * Populates hero_chinese_names using a mapping loaded from a JSON file in resources.
     * This is a final fallback when all other methods fail.
     * The JSON file contains a comprehensive mapping of hero IDs to Chinese names.
     */
    private int populateHeroChineseNamesFromHardcodedMapping() throws SQLException, IOException {
        // Load hero Chinese names from JSON file in resources
        Map<Integer, String> heroChineseNames = loadHeroChineseNamesFromResource();
        
        if (heroChineseNames.isEmpty()) {
            LOGGER.warn("No hero Chinese names loaded from resource file");
            return 0;
        }
        
        try (Connection connection = DotaConstantsDatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int rows = 0;
                String sql = "INSERT INTO " + SCHEMA + ".hero_chinese_names (hero_id, chinese_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (hero_id) DO UPDATE SET chinese_name = EXCLUDED.chinese_name, updated_at = CURRENT_TIMESTAMP";
                
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (Map.Entry<Integer, String> entry : heroChineseNames.entrySet()) {
                        statement.setInt(1, entry.getKey());
                        statement.setString(2, entry.getValue());
                        statement.executeUpdate();
                        rows++;
                    }
                }
                
                connection.commit();
                return rows;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    /**
     * Loads hero Chinese names from the JSON file in resources.
     * 
     * @return Map of hero ID to Chinese name
     * @throws IOException if the resource file cannot be read
     */
    private Map<Integer, String> loadHeroChineseNamesFromResource() throws IOException {
        Map<Integer, String> heroChineseNames = new LinkedHashMap<>();
        
        try {
            ClassPathResource resource = new ClassPathResource("hero_chinese_names.json");
            if (!resource.exists()) {
                LOGGER.warn("hero_chinese_names.json not found in resources");
                return heroChineseNames;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);
                
                // The JSON file has hero IDs as keys and Chinese names as values
                for (var iterator = rootNode.fields(); iterator.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = iterator.next();
                    try {
                        int heroId = Integer.parseInt(entry.getKey());
                        String chineseName = entry.getValue().asText();
                        if (chineseName != null && !chineseName.isEmpty()) {
                            heroChineseNames.put(heroId, chineseName);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.debug("Skipping invalid hero ID in resource file: {}", entry.getKey());
                    }
                }
            }
            
            LOGGER.info("Loaded {} hero Chinese names from resource file", heroChineseNames.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load hero_chinese_names.json from resources", e);
            throw e;
        }
        
        return heroChineseNames;
    }

    private record RemoteJsonFile(String name, String downloadUrl) {}
}



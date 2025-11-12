package opendota.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import opendota.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReplayProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ReplayProcessingService.class);
    private static final int BUFFER_SIZE = 8192;

    private final S3Service s3Service;

    public ReplayProcessingService(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    public ReplayResponse processRemoteReplay(String replayUrl) {
        try {
            if (S3Service.isS3Url(replayUrl)) {
                return handleS3Replay(replayUrl);
            }
            return handleHttpReplay(replayUrl);
        } catch (IOException ex) {
            log.error("Failed to process remote replay {}", replayUrl, ex);
            return new ReplayResponse(500, new byte[0]);
        }
    }

    public ReplayResponse processLocalReplay(String filePath) {
        try {
            return handleLocalReplay(filePath);
        } catch (IOException ex) {
            log.error("Failed to process local replay {}", filePath, ex);
            return new ReplayResponse(500, new byte[0]);
        }
    }

    private ReplayResponse handleS3Replay(String s3Url) throws IOException {
        log.warn("Processing S3 replay: {}", s3Url);
        String decompressCmd = s3Url.endsWith(".bz2") ? "bunzip2" : "cat";
        String command = String.format(
                "%s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs",
                decompressCmd);

        Process process = new ProcessBuilder("bash", "-c", command).start();

        try (InputStream s3Stream = s3Service.downloadFromS3(s3Url);
             OutputStream processInput = process.getOutputStream()) {
            copy(s3Stream, processInput);
        }

        return buildReplayResponse(process);
    }

    private ReplayResponse handleHttpReplay(String replayUrl) throws IOException {
        log.warn("Processing HTTP replay: {}", replayUrl);
        String decompressCmd = replayUrl.endsWith(".bz2") ? "bunzip2" : "cat";
        String command = String.format(
                "curl --max-time 145 --fail -L %s | %s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs",
                replayUrl,
                decompressCmd);

        Process process = new ProcessBuilder("bash", "-c", command).start();
        return buildReplayResponse(process);
    }

    private ReplayResponse handleLocalReplay(String filePath) throws IOException {
        log.warn("Processing local replay: {}", filePath);
        File replayFile = new File(filePath);
        if (!replayFile.exists()) {
            log.error("File not found: {}", filePath);
            return new ReplayResponse(404, new byte[0]);
        }
        if (!replayFile.canRead()) {
            log.error("File not readable: {}", filePath);
            return new ReplayResponse(403, new byte[0]);
        }

        String decompressCmd = filePath.endsWith(".bz2") ? "bunzip2" : "cat";
        String command = String.format(
                "%s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs",
                decompressCmd);

        Process process = new ProcessBuilder("bash", "-c", command).start();

        try (InputStream fileStream = new FileInputStream(replayFile);
             OutputStream processInput = process.getOutputStream()) {
            copy(fileStream, processInput);
        }

        return buildReplayResponse(process);
    }

    private ReplayResponse buildReplayResponse(Process process) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        try (InputStream processOutput = process.getInputStream();
             InputStream processError = process.getErrorStream()) {
            copy(processOutput, outputStream);
            copy(processError, errorStream);
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Process interrupted", ex);
            return new ReplayResponse(500, new byte[0]);
        }

        String error = errorStream.toString(StandardCharsets.UTF_8);
        if (!error.isBlank()) {
            log.warn("Replay processing stderr: {}", error);
        }

        if (exitCode != 0) {
            int status = 500;

            if (error.contains("curl: (28) Operation timed out")
                    || error.contains("curl: (22) The requested URL returned error: 502")
                    || error.contains("bunzip2: Data integrity error when decompressing")
                    || error.contains("bunzip2: Compressed file ends unexpectedly")
                    || error.contains("bunzip2: (stdin) is not a bzip2 file.")) {
                status = 200;
            }

            if (error.contains("S3 download error")) {
                log.error("S3 download failed: {}", error);
            }

            return new ReplayResponse(status, new byte[0]);
        }

        return new ReplayResponse(200, outputStream.toByteArray());
    }

    private void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = source.read(buffer)) != -1) {
            sink.write(buffer, 0, read);
        }
    }

    public record ReplayResponse(int status, byte[] body) {
    }
}


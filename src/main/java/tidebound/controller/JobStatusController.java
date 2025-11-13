package tidebound.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import tidebound.service.ReplayJobService;
import tidebound.service.ReplayJobService.ReplayJobSnapshot;

@RestController
public class JobStatusController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final ReplayJobService replayJobService;

    public JobStatusController(ReplayJobService replayJobService) {
        this.replayJobService = replayJobService;
    }

    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> jobStatusPage() {
        List<ReplayJobSnapshot> jobs = replayJobService.listJobs();
        String body = buildHtml(jobs);

        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private String buildHtml(List<ReplayJobSnapshot> jobs) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8" />
                <title>Replay Job Status</title>
                <meta http-equiv="refresh" content="10" />
                <style>
                body { font-family: Arial, sans-serif; margin: 2rem; background-color: #f5f5f5; color: #333; }
                h1 { margin-bottom: 1rem; }
                table { width: 100%; border-collapse: collapse; background: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                th, td { padding: 0.75rem; border-bottom: 1px solid #ddd; text-align: left; }
                th { background-color: #f0f0f0; }
                tr:hover { background-color: #f9f9f9; }
                .status-PENDING { color: #555; }
                .status-RUNNING { color: #1a73e8; }
                .status-SUCCEEDED { color: #0b8043; }
                .status-FAILED { color: #c5221f; }
                .empty { margin-top: 1rem; font-style: italic; }
                </style>
                </head>
                <body>
                <h1>Replay Job Status</h1>
                """);

        if (jobs.isEmpty()) {
            html.append("""
                    <p class="empty">No jobs have been submitted yet.</p>
                    """);
        } else {
            html.append("""
                    <table>
                    <thead>
                    <tr>
                    <th>Job ID</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Source</th>
                    <th>Parser Status</th>
                    <th>Created</th>
                    <th>Updated</th>
                    <th>Error</th>
                    </tr>
                    </thead>
                    <tbody>
                    """);

            for (ReplayJobSnapshot job : jobs) {
                String statusClass = "status-" + job.status().name();
                html.append("<tr>");
                html.append("<td>")
                        .append(HtmlUtils.htmlEscape(job.id().toString()))
                        .append("</td>");
                html.append("<td>")
                        .append(HtmlUtils.htmlEscape(job.type().name()))
                        .append("</td>");
                html.append("<td class=\"")
                        .append(statusClass)
                        .append("\">")
                        .append(HtmlUtils.htmlEscape(job.status().name()))
                        .append("</td>");
                html.append("<td>")
                        .append(escapeOrDash(job.source()))
                        .append("</td>");
                html.append("<td>")
                        .append(job.parserStatus() != null ? HtmlUtils.htmlEscape(job.parserStatus().toString()) : "-")
                        .append("</td>");
                html.append("<td>")
                        .append(formatInstant(job.createdAt()))
                        .append("</td>");
                html.append("<td>")
                        .append(formatInstant(job.updatedAt()))
                        .append("</td>");
                html.append("<td>")
                        .append(escapeOrDash(job.errorMessage()))
                        .append("</td>");
                html.append("</tr>");
            }

            html.append("""
                    </tbody>
                    </table>
                    """);
        }

        html.append("""
                </body>
                </html>
                """);

        return html.toString();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? HtmlUtils.htmlEscape(DATE_FORMATTER.format(instant)) : "-";
    }

    private String escapeOrDash(String value) {
        return value != null ? HtmlUtils.htmlEscape(value) : "-";
    }
}



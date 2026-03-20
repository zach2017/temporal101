package com.fileshuttle.cli;

import com.fileshuttle.model.LocationType;
import com.fileshuttle.model.TransferRequest;
import com.fileshuttle.model.TransferResult;
import com.fileshuttle.provider.ProviderFactory;
import com.fileshuttle.provider.S3Provider;
import com.fileshuttle.provider.TransferOrchestrator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI entry point for File Shuttle.
 *
 * EXAMPLES:
 *   # Local → S3
 *   file-shuttle -f report.csv -it local -i /data/report.csv -ot s3 -o s3://bucket/report.csv
 *
 *   # S3 → NFS
 *   file-shuttle -f backup.tar.gz -it s3 -i s3://backups/backup.tar.gz -ot nfs -o /mnt/nfs/backup.tar.gz
 *
 *   # URL → S3
 *   file-shuttle -f data.zip -it url -i https://example.com/data.zip -ot s3 -o s3://datasets/data.zip
 *
 *   # S3 → URL (POST)
 *   file-shuttle -f export.json -it s3 -i s3://exports/data.json -ot url -o https://api.example.com/upload --http-method POST
 */
@Command(name = "file-shuttle", mixinStandardHelpOptions = true, version = "1.0.0",
    description = "Memory-efficient streaming file transfer: S3, NFS, URL, Local")
public class FileShuttleApp implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, required = true,
            description = "Logical filename (for logging)")
    private String fileName;

    @Option(names = {"--input-type", "-it"}, required = true,
            description = "Input type: local, s3, nfs, url")
    private String inputType;

    @Option(names = {"--input", "-i"}, required = true,
            description = "Input location (path, s3://bucket/key, or URL)")
    private String inputLocation;

    @Option(names = {"--output-type", "-ot"}, required = true,
            description = "Output type: s3, nfs, url")
    private String outputType;

    @Option(names = {"--output", "-o"}, required = true,
            description = "Output location (s3://bucket/key, path, or URL)")
    private String outputLocation;

    @Option(names = {"--buffer-size", "-bs"}, defaultValue = "65536",
            description = "Stream buffer size in bytes (default: 64KB)")
    private int bufferSize;

    @Option(names = {"--multipart-size", "-ms"}, defaultValue = "8388608",
            description = "S3 multipart part size in bytes (default: 8MB)")
    private long multipartSize;

    @Option(names = {"--http-method"}, defaultValue = "PUT",
            description = "HTTP method for URL output: PUT or POST")
    private String httpMethod;

    @Option(names = {"--s3-endpoint"},
            description = "S3-compatible endpoint URL (e.g. http://minio:9000)")
    private String s3Endpoint;

    @Override
    public Integer call() {
        try {
            Map<String, String> options = new HashMap<>();
            options.put("bufferSize", String.valueOf(bufferSize));
            options.put("multipartPartSize", String.valueOf(multipartSize));
            options.put("httpMethod", httpMethod);

            LocationType inType  = LocationType.fromString(inputType);
            LocationType outType = LocationType.fromString(outputType);

            TransferRequest request = new TransferRequest(
                    fileName, inType, inputLocation, outType, outputLocation, options);

            ProviderFactory factory = new ProviderFactory();

            if (s3Endpoint != null && !s3Endpoint.isBlank()) {
                String accessKey = env("AWS_ACCESS_KEY_ID", "minioadmin");
                String secretKey = env("AWS_SECRET_ACCESS_KEY", "minioadmin");
                String region    = env("AWS_REGION", "us-east-1");
                factory.register(LocationType.S3,
                        S3Provider.forEndpoint(s3Endpoint, accessKey, secretKey, region));
            }

            TransferOrchestrator orchestrator = new TransferOrchestrator(factory);
            TransferResult result = orchestrator.execute(request);
            System.out.println(result.summary());
            return result.success() ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            e.printStackTrace(System.err);
            return 2;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FileShuttleApp()).execute(args);
        System.exit(exitCode);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}

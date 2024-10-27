package com.yoshio3;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.CosmosDBInput;
import com.microsoft.azure.functions.annotation.BlobTrigger;

import java.util.Optional;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.URL;

/**
 * Azure Functions with HTTP Trigger, Cosmos DB Trigger, and Blob Trigger.
 */
public class Function {
    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }

    /**
     * This function is triggered when a new document is created in the specified Cosmos DB collection.
     * It reads the created state information and outputs it to the context log.
     */
    @FunctionName("CosmosDBTriggerExample")
    public void cosmosDBTrigger(
            @CosmosDBTrigger(
                name = "cosmosDBTrigger",
                databaseName = "YourDatabaseName",
                collectionName = "YourCollectionName",
                connectionStringSetting = "AzureWebJobsCosmosDBConnectionString",
                leaseCollectionName = "leases",
                createLeaseCollectionIfNotExists = true)
                List<String> documents,
            final ExecutionContext context) {
        context.getLogger().info("Java Cosmos DB trigger processed a request.");

        for (String document : documents) {
            context.getLogger().info(document);
        }
    }

    /**
     * This function is triggered when a new file is uploaded to the specified Blob Storage container.
     * It reads the content of text files and displays it, and displays the URL of image and sound files.
     */
    @FunctionName("BlobTriggerExample")
    public void blobTrigger(
            @BlobTrigger(
                name = "blobTrigger",
                path = "samples-workitems/{name}",
                connection = "AzureWebJobsStorage")
                byte[] content,
            @BindingName("name") String filename,
            final ExecutionContext context) {
        context.getLogger().info("Java Blob trigger processed a request for file: " + filename);

        if (filename.endsWith(".txt")) {
            String fileContent = new String(content, StandardCharsets.UTF_8);
            context.getLogger().info("Text file content: " + fileContent);
        } else if (filename.endsWith(".jpg") || filename.endsWith(".png")) {
            String fileUrl = "https://<your-storage-account-name>.blob.core.windows.net/samples-workitems/" + filename;
            context.getLogger().info("Image file URL: " + fileUrl);
        } else if (filename.endsWith(".mp3") || filename.endsWith(".wav")) {
            String fileUrl = "https://<your-storage-account-name>.blob.core.windows.net/samples-workitems/" + filename;
            context.getLogger().info("Sound file URL: " + fileUrl);
        } else {
            context.getLogger().info("Unsupported file type: " + filename);
        }
    }
}

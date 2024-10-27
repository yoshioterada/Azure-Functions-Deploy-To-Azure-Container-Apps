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
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.json.JSONObject;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.CompletionsOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;

/**
 * Azure Functions with HTTP Trigger, Cosmos DB Trigger, and Blob Trigger.
 */
public class Function {
    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?question=HTTP%20Query"
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
        final String query = request.getQueryParameters().get("question");
        final String question = request.getBody().orElse(query);

        if (question == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a question on the query string or in the request body").build();
        } else {
            try {
                String response = queryGPT4o(question);
                return request.createResponseBuilder(HttpStatus.OK).body(response).build();
            } catch (IOException e) {
                context.getLogger().severe("Error querying GPT-4o: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("Error querying GPT-4o").build();
            }
        }
    }

    private String queryGPT4o(String question) throws IOException {
        OpenAIClient client = new OpenAIClientBuilder()
            .credential(new DefaultAzureCredentialBuilder().build())
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .buildClient();

        CompletionsOptions options = new CompletionsOptions()
            .setPrompt(question)
            .setMaxTokens(100);

        return client.getCompletions("gpt-4o", options).getChoices().get(0).getText();
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

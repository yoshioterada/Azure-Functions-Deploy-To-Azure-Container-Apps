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

import java.util.Optional;
import java.util.List;

/**
 * Azure Functions with HTTP Trigger and Cosmos DB Trigger.
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
}

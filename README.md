# Deploying a Java Azure Function on Azure Container Apps

Azure Functions provides integrated support for developing, deploying, and managing containerized function apps on Azure Container Apps. This makes it easier to run and manage Azure Functions using the integrated Azure management portal, compared to running Azure Functions independently in container environments like Azure Kubernetes Service (AKS). Additionally, by leveraging features provided by Azure Container Apps, you can easily utilize functionalities such as KEDA, Dapr, Envoy, scaling, monitoring, security, and access control for your Azure Functions.

[Reference]  
[Azure Container Apps hosting of Azure Functions](https://learn.microsoft.com/azure/azure-functions/functions-container-apps-hosting)  
[Create your first containerized functions on Azure Container Apps](https://learn.microsoft.com/azure/azure-functions/functions-deploy-container-apps?tabs=acr%2Cbash&pivots=programming-language-java)

## Setting Environment Variables

Below are the environment variables related to creating Azure Container Apps resources. Here, you specify various names and installation locations for the resources you will create, as well as the container image name and tag.

```bash
# Azure Container Apps resource names
export LOCATION=eastus
export RESOURCE_GROUP_NAME=yoshio-rg
export CONTAINER_REGISTRY_NAME=cajava2411
export CONTAINER_ENVIRONMENT=YoshioContainerEnvironment
export STORAGE_NAME=yoshiojavastorage
export AZURE_FUNCTION_NAME=yoshiojavafunc

# Container image name and tag
export C_IMAGE_NAME=tyoshio2002/java-function-on-aca
export C_IMAGE_TAG=1.0
```

## Creating and Testing a Java Azure Function Project

### 1. Create an Azure Functions for Java Maven Project

First, create a Maven project for Azure Functions for Java. This Maven project is designed for creating Azure Functions using Java 21. Use the `mvn archetype:generate` command to create the project, modifying parameters as needed.

```bash
mvn archetype:generate \
-DinteractiveMode=false \
-DarchetypeGroupId=com.microsoft.azure \
-DarchetypeArtifactId=azure-functions-archetype \
-DgroupId=com.yoshio3 \
-Dpackage=com.yoshio3 \
-DartifactId=yoshiojavafunc \
-DappName=Java-Azure-Functions \
-DappRegion=$LOCATION \
-DjavaVersion=21 \
-Dversion=1.0-SNAPSHOT \
-Ddocker
```

Executing the above command will automatically create a directory structure, and `Function.java` will contain sample code for an Azure Function with an HTTP trigger. A `Dockerfile` will also be created, which contains the configuration for running Azure Functions in a Docker container environment.

```text
├── Dockerfile
├── host.json
├── local.settings.json
├── pom.xml
└── src
    ├── main
    │   └── java
    │       └── com
    │           └── yoshio3
    │               └── Function.java
    └── test
        └── java
            └── com
                └── yoshio3
                    ├── FunctionTest.java
                    └── HttpResponseMessageMock.java
```

### 2. Run Azure Function Locally

Build the Maven project and run the Azure Functions locally. Execute the following commands to start the Azure Functions with the HTTP trigger.

```bash
mvn clean package
mvn azure-functions:run
```

Once the Azure Functions are running, open another terminal and execute the following command to send a request to the HTTP trigger. You should receive a response saying "Hello, World".

```bash
curl "http://localhost:7071/api/HttpExample?name=World"
# Output: 
Hello, World
```

## Creating and Testing the Azure Functions Container Image

### 1. Build and Test the Docker Image

Use the automatically generated `Dockerfile` to build the Azure Functions container image. Execute the following command to build the image.

```bash
docker build -t $C_IMAGE_NAME:$C_IMAGE_TAG -f ./Dockerfile .
```

After the build is complete, run the following command to check if the image has been created.

```bash
docker images | grep $C_IMAGE_NAME
# Output: 
tyoshio2002/java-function-on-aca    1.0    bcf471e6f774   9 hours ago     1.46GB
```

Once the image is created, run the following command to test the Azure Functions container image locally. The Azure Functions container internally uses HTTP port 80, so you will map it to port 8080 for local access. After the container starts, execute the curl command to send a request to the Azure Functions HTTP trigger. If everything is working correctly, you should receive "Hello, World".

```bash
docker run -p 8080:80 -it $C_IMAGE_NAME:$C_IMAGE_TAG
curl "http://localhost:8080/api/HttpExample?name=World"
# Output: 
Hello, World
```

## Pushing the Container Image to Azure Container Registry

### 1. Log in to Azure CLI

First, log in to Azure using the Azure CLI. Execute the following command to log in.

```bash
az login
```

### 2. Create a Resource Group

Create a resource group in Azure. This resource group will be used to group resources related to Azure Container Registry and Azure Container Apps.

```bash
az group create --name $RESOURCE_GROUP_NAME --location $LOCATION
```

### 3. Create and Log in to Azure Container Registry

Create an Azure Container Registry and log in. Azure Container Registry is a private container registry for pushing container images.

```bash
az acr create --resource-group $RESOURCE_GROUP_NAME --name $CONTAINER_REGISTRY_NAME --sku Basic
az acr login --name $CONTAINER_REGISTRY_NAME
```

### 4. Retrieve the Azure Container Registry Server Name

Retrieve the server name of the created Azure Container Registry. The server name will be in the format `$CONTAINER_REGISTRY_NAME.azurecr.io`.

```bash
CONTAINER_REGISTRY_SERVER=$(az acr show --name $CONTAINER_REGISTRY_NAME --query loginServer --output tsv)
```

### 5. Push the Image to Azure Container Registry

To push the locally created container image to Azure Container Registry, tag the image using the `tag` command. After tagging, use the `push` command to push the image.

```bash
docker tag $C_IMAGE_NAME:$C_IMAGE_TAG $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG
docker push $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG
```

## Creating Azure Container Apps and Deploying the Java Azure Function

### 1. Register Extensions and Resource Providers in Azure CLI

To create and manage Azure Container Apps from Azure CLI, register the necessary extensions and resource providers.

```bash
az upgrade
az extension add --name containerapp --upgrade -y
az provider register --namespace Microsoft.Web
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
```

### 2. Create Azure Container Apps Environment

Create an environment for Azure Container Apps. This command sets up the configuration needed to host Azure Container Apps.

```bash
az containerapp env create --name $CONTAINER_ENVIRONMENT --enable-workload-profiles --resource-group $RESOURCE_GROUP_NAME --location $LOCATION
```

### 3. Create Storage Account for Azure Functions

Azure Functions requires a storage account when creating a Function App instance. Therefore, create a general-purpose storage account for Azure Functions.

```bash
az storage account create --name $STORAGE_NAME --location $LOCATION --resource-group $RESOURCE_GROUP_NAME --sku Standard_LRS
```

### 4. Create an Instance of Java Azure Function in Azure Container Apps

Create an instance of the Java Azure Function in Azure Container Apps. Execute the following command to create the instance. Since the Azure Function is created using Java 21, specify `--runtime java`.

```bash
az functionapp create --name $AZURE_FUNCTION_NAME \
--resource-group $RESOURCE_GROUP_NAME \
--environment $CONTAINER_ENVIRONMENT \
--storage-account $STORAGE_NAME \
--workload-profile-name "Consumption" \
--max-replicas 15 \
--min-replicas 1 \
--functions-version 4 \
--runtime java \
--image $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG \
--assign-identity
```

### 5. Assign Role for Azure Function to Access Azure Container Registry

Finally, configure secure access for Azure Functions to Azure Container Registry. Enable the system-managed identity for Azure Functions and assign the `ACRPull` role for access.

```bash
FUNCTION_APP_ID=$(az functionapp identity assign --name $AZURE_FUNCTION_NAME --resource-group $RESOURCE_GROUP_NAME --query principalId --output tsv)
ACR_ID=$(az acr show --name $CONTAINER_REGISTRY_NAME --query id --output tsv)
az role assignment create --assignee $FUNCTION_APP_ID --role AcrPull --scope $ACR_ID
```

### 6. Retrieve the URL of the Azure Function

Finally, retrieve the HTTP trigger function URL of the deployed Azure Function. Use the `az functionapp function show` command to get the details of the Azure Functions function.

```azurecli
az functionapp function show --resource-group $RESOURCE_GROUP_NAME --name $AZURE_FUNCTION_NAME --function-name HttpExample --query invokeUrlTemplate
# Output: "https://yoshiojavafunc.niceocean-********.eastus.azurecontainerapps.io/api/httpexample"
```

You can then send a request to the retrieved URL using `curl` command to confirm that the Azure Functions is working correctly.

```bash
curl "https://yoshiojavafunc.niceocean-********.eastus.azurecontainerapps.io/api/httpexample?name=World"
# Expected Output: 
Hello, World
```

If everything is set up correctly, you should receive a response saying "Hello, World", confirming that your Azure Function is functioning as expected.

## Setting Up Cosmos DB Trigger

### 1. Add Cosmos DB Trigger Function

Add a new function with Cosmos DB Trigger to read created state information and output it to the context log. Update the `Function.java` file to include the new function.

```java
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
```

### 2. Update `host.json`

Configure Cosmos DB Trigger settings in the `host.json` file.

```json
{
  "version": "2.0",
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[3.*, 4.0.0)"
  },
  "extensions": {
    "cosmosDB": {
      "connectionStringSetting": "AzureWebJobsCosmosDBConnectionString"
    }
  }
}
```

### 3. Update `pom.xml`

Add dependencies for Cosmos DB in the `pom.xml` file.

```xml
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>azure-cosmosdb</artifactId>
    <version>2.6.3</version>
</dependency>
```

### 4. Deploy and Test

Deploy the updated Azure Function to Azure Container Apps and test the Cosmos DB Trigger by creating a new document in the specified Cosmos DB collection. Verify that the created state information is output to the context log.

## Summary

In this guide, you learned how to:

1. Set up environment variables for your Azure resources.
2. Create a Java Azure Function project using Maven.
3. Run the Azure Function locally for testing.
4. Build a Docker image for the Azure Function.
5. Push the Docker image to Azure Container Registry.
6. Create an Azure Container Apps environment and deploy the Java Azure Function.
7. Assign necessary roles for secure access to Azure Container Registry.
8. Retrieve and test the URL of the deployed Azure Function.
9. Set up a Cosmos DB Trigger to read created state information and output it to the context log.

By following these steps, you can successfully deploy a Java Azure Function on Azure Container Apps, leveraging the benefits of containerization and Azure's integrated management capabilities. If you have any further questions or need assistance with specific steps, feel free to ask!

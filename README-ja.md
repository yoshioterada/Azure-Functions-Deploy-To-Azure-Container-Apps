# Azure Container Apps に対して Java Azure Function on Linnux Container をデプロイする

Azure Functions では、Azure Container Apps でコンテナー化した関数アプリを開発、デプロイ、管理するための統合サポートを提供しています。
これにより、Azure Kubernetes Service (AKS) などのコンテナー環境で Azure Functions を独自に実行するよりも、統合された Azure の管理ポータルを使用して、Azure Functions を簡単に実行・管理することができます。さらに Azure Container Apps が提供する機能を使用することで、Azure Functions に対して、KEDA、Dapr, Envoy、スケーリング、監視、セキュリティ、アクセス制御などの機能を簡単に利用することができます。

https://learn.microsoft.com/azure/azure-functions/functions-container-apps-hosting
https://learn.microsoft.com/azure/azure-functions/functions-deploy-container-apps?tabs=acr%2Cbash&pivots=programming-language-java

## 環境変数の設定

下記に、Azure Container Apps 作成に関連するリソース名を設定します。ここでは作成するリソースの各種名前やインストール・ロケーションを指定します。
さらに、コンテナのイメージ名とタグを設定します。

```bash
# Azure Container Apps 作成に関連するリソース名
export LOCATION=eastus
export RESOURCE_GROUP_NAME=yoshio-rg
export CONTAINER_REGISTRY_NAME=cajava2411
export CONTAINER_ENVIRONMENT=YoshioContainerEnvironment
export STORAGE_NAME=yoshiojavastorage
export AZURE_FUNCTION_NAME=yoshiojavafunc

# コンテナのイメージ名とタグ
export C_IMAGE_NAME=tyoshio2002/java-function-on-aca
export C_IMAGE_TAG=1.0
```

## Java Azure Function のプロジェクト作成と動作確認

### 1. Azure Functions for Java Maven プロジェクトの作成

まず、Azure Functions for Java の Maven プロジェクトを作成します。この Maven プロジェクトは、Java 21 用の Azure Functions を作成するための Maven プロジェクトです。Maven の archetype:generate コマンドを使用して、Azure Functions for Java の Maven プロジェクトを作成しています。必要に応じて、下記のパラメータを変更してください。

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

上記のコマンドを実行すると、下記のようなディレクトリ構造が自動的に作成され、Function.java には、HTTP トリガーを持つ Azure Function のサンプルコードが作成されています。
また、Dockerfile が作成されており、Azure Functions を Docker コンテナ環境で実行するための Docker イメージの設定が記述されています。これにより、Azure Functions をコンテナ環境で実行する事ができます。

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

### 2. ローカルで Azure Function を実行

作成した Maven プロジェクトをビルドして、ローカルで Azure Functions を実行します。下記のコマンドを実行すると、ローカルで Azure Functions を実行し、HTTP トリガーを持つ Azure Function が起動します。

```bash
mvn clean package
mvn azure-functions:run
```

Azure Functions が起動したら、別のターミナルを開いて、下記のコマンドを実行して、HTTP トリガーを持つ Azure Function にリクエストを送信します。
実行すると "Hello, World" というレスポンスが返ってきます。

```bash
curl "http://localhost:7071/api/HttpExample?name=World"
Hello, World
```

## Azure Functions のコンテナイメージの作成と動作確認

### 1. Docker イメージのビルドと動作確認

自動生成された Dockerfile を使用して、Azure Functions のコンテナ・イメージをビルドします。下記のコマンドを実行して、Azure Functions のコンテナイメージをビルドします。

```bash
docker build -t $C_IMAGE_NAME:$C_IMAGE_TAG  -f ./Dockerfile . 
```

ビルドが完了したら、下記のコマンドを実行してイメージが作成されているか確認します。

```bash
> docker images | grep $C_IMAGE_NAME                                
tyoshio2002/java-function-on-aca      1.0      bcf471e6f774   6 hours ago     1.46GB
```

イメージを作成した後、下記のコマンドを実行してローカル環境で Azure Functions のコンテナイメージの動作確認を行います。
この際、Azure Functions のコンテナは内部的に HTTP の 80 番ポートを使用しサービスを公開しているため、ローカル環境からアクセスするために 8080 番ポートにマッピングしています。コンテナが起動したら、下記の curl コマンドを実行し、Azure Functions の HTTP トリガーに対してリクエストを送信します。正常に起動している場合、"Hello, World" というレスポンスが返ってきます。

```bash
docker run -p 8080:80 -it $C_IMAGE_NAME:$C_IMAGE_TAG

curl "http://localhost:8080/api/HttpExample?name=World"

Hello, World
```

## Azure Container Registry にコンテナ・イメージをプッシュ

### 1. Azure CLI でログイン

まず始めに、Azure CLI で Azure にログインします。下記のコマンドを実行して、Azure にログインします。

```azurecli
az login
```

### 2. リソースグループの作成

Azure にリソースグループを作成します。このリソースグループは、Azure Container Registry や Azure Container Apps に関連するリソースをグループ化するために使用します。

```azurecli
az group create --name $RESOURCE_GROUP_NAME --location $LOCATION
```

### 3. Azure Container Registry の作成とログイン

Azure Container Registry を作成し、ログインします。Azure Container Registry はコンテナ・イメージをプッシュするためのプライベートな、コンテナ・レジストリです。

```azurecli
az acr create --resource-group $RESOURCE_GROUP_NAME --name $CONTAINER_REGISTRY_NAME --sku Basic
az acr login --name $CONTAINER_REGISTRY_NAME
```

### 4. Azure Container Registry のサーバー名を取得

作成した Azure Container Registry のサーバー名を取得します。取得したサーバ名は `$CONTAINER_REGISTRY_NAME.azurecr.io` の形式で取得できます。

```azurecli
CONTAINER_REGISTRY_SERVER=$(az acr show --name $CONTAINER_REGISTRY_NAME --query loginServer --output tsv)
```

### 5.  Azure Container Registry にイメージをプッシュ

ローカルに作成したコンテナ・イメージを Azure Container Registry にプッシュするために、'tag' コマンドでタグ付けを行います。タグ付けが完了したら、'push' コマンドを実行して、Azure Container Registry にコンテナ・イメージをプッシュします。

```bash
docker tag $C_IMAGE_NAME:$C_IMAGE_TAG $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG
docekr push $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG
```

## Azure Container Apps の作成と Java Azure Function のデプロイ

### 1. Azure CLI に拡張機能とリソースプロバイダーの登録

Azure Container Apps を Azure CLI から作成・管理するため、Azure CLI に対して拡張機能とリソースプロバイダーを登録します。

```azurecli
az upgrade
az extension add --name containerapp --upgrade -y
az provider register --namespace Microsoft.Web 
az provider register --namespace Microsoft.App 
az provider register --namespace Microsoft.OperationalInsights 
```

### 2. Azure Container Apps に関連するリソースを作成

Azure Container Apps の環境を作成します。下記のコマンドを実行すると、Azure Container Apps をホストするための環境設定ができるようになります。

```azurecli
az containerapp env create --name $CONTAINER_ENVIRONMENT --enable-workload-profiles --resource-group $RESOURCE_GROUP_NAME --location $LOCATION
```

### 3. Azure Container Apps に関連するリソースを作成(ストレージアカウント、Function App)

Azure Functions では、Function App インスタンスを作成する際に Azure ストレージ アカウントが必要になります。そこで Azure Functions 用の汎用ストレージ・アカウントを作成します。

```azurecli
az storage account create --name $STORAGE_NAME --location $LOCATION --resource-group $RESOURCE_GROUP_NAME --sku Standard_LRS
```

### 4. Azure Container Apps に Java Azure Function のインスタンスを作成

Azure Container Apps に Java Azure Function のインスタンスを作成します。下記のコマンドを実行して、Azure Container Apps に Java Azure Function のインスタンスを作成します。今回デプロイする Azure Function は、Java 21 で作成された Azure Function のため、`--runtime java` を指定しています。

```azurecli
az functionapp create --name $AZURE_FUNCTION_NAME \
--resource-group $RESOURCE_GROUP_NAME \
--environment $CONTAINER_ENVIRONMENT \
--storage-account $STORAGE_NAME \
--workload-profile-name "Consumption" \
--max-replicas 15 --min-replicas 1 \
--functions-version 4 \
--runtime java \
--image $CONTAINER_REGISTRY_SERVER/$C_IMAGE_NAME:$C_IMAGE_TAG \
--assign-identity
```

### 5. Azure Function から Azure Container Registry にアクセスするためのロールを割り当てる

最後にセキュアに Azure Functions から Azure Container Registry にアクセスをするための設定を行います。

Azure Functions から Azure Container Registry にアクセスするために、Azure Functions でシステム・マネージド ID を有効にし、Azure Container Registry にアクセスするための `ACRPull` のロールを割り当てます。ここでは、`az functionapp identity assign` で Azure Functions にシステム・マネージド ID を割り当て、`az role assignment create` で Azure Functions に対して `ACRPull` のロールを割り当てています。

```azurecli
FUNCTION_APP_ID=$(az functionapp identity assign --name $AZURE_FUNCTION_NAME --resource-group $RESOURCE_GROUP_NAME --query principalId --output tsv)
ACR_ID=$(az acr show --name $CONTAINER_REGISTRY_NAME --query id --output tsv)
az role assignment create --assignee $FUNCTION_APP_ID --role AcrPull --scope $ACR_ID
```

### 6. Azure Function の URL を取得

最後に、デプロイした Azure Function の HTTPTrigger のファンクションの URL を取得します。
`az functionapp function show` コマンドは Azure Functions 関数の詳細情報を取得するためのコマンドです。ここでは、Function App の名前、リソース・グループの名前、関数の名前を指定して、HTTPTrigger の関数の URL を `--query invokeUrlTemplate` を利用して取得しています。

```azurecli
az functionapp function show --resource-group $RESOURCE_GROUP_NAME --name $AZURE_FUNCTION_NAME --function-name HttpExample --query invokeUrlTemplate

"https://yoshiojavafunc.niceocean-********.eastus.azurecontainerapps.io/api/httpexample"
```

そして、取得した URL に対して curl コマンドで取得した URL に対してリクエストを送信すると、Azure Functions が正常に動作していることを確認できます。

```bash
> curl "https://yoshiojavafunc.niceocean-********.eastus.azurecontainerapps.io/api/httpexample?name=World"
Hello, World
```

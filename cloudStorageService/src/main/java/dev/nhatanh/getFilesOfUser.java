package dev.nhatanh;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.HashMap;
import java.util.Map;

public class getFilesOfUser implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        String tableName = "Users";
        String userID = input.getQueryStringParameters().get("userID");
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("userID", AttributeValue.builder().s(userID).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();
        
        Map<String, AttributeValue> response = ddb.getItem(request).item();
        if (response.isEmpty()) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withHeaders(corsHeaders)
                    .withBody("User not found");
        }

        if (!response.containsKey("associatedFiles")) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(corsHeaders)
                    .withBody("{}");
        }

        Map<String, String> data = new HashMap<>();

        String fileTableName = "Files";
        response.get("associatedFiles").ss().forEach(fileID -> {
            GetItemRequest fileRequest = GetItemRequest.builder()
                    .tableName(fileTableName)
                    .key(Map.of("fileID", AttributeValue.builder().s(fileID).build()))
                    .build();
            
            Map<String, AttributeValue> fileItem = ddb.getItem(fileRequest).item();
            String id = fileItem.get("fileID").s();
            String name = fileItem.get("fileName").s();
            String uploadTime = fileItem.get("uploadDate").s();

            data.put(name, String.format("{\"fileID\": \"%s\", \"uploadDate\": \"%s\"}", id, uploadTime));
        });

        String responseJson = "{";
        for (Map.Entry<String, String> entry : data.entrySet()) {
            responseJson += String.format("\"%s\": %s,", entry.getKey(), entry.getValue());
        }
        responseJson = responseJson.substring(0, responseJson.length() - 1) + "}";

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(corsHeaders)
                .withBody(responseJson);
    }
    
}
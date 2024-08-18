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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class downloadFile implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        String tableName = "Files";
        String bucketName = "cloud-storage-service";
        String fileID = input.getQueryStringParameters().get("fileID");
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("fileID", AttributeValue.builder().s(fileID).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();
        
        Map<String, AttributeValue> response = ddb.getItem(request).item();
        String fileName = response.get("fileName").s();

        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(java.time.Duration.ofMinutes(5))
                    .getObjectRequest(objectRequest)
                    .build();

            String url = presigner.presignGetObject(presignRequest).url().toString();
            String urlJson = "{\"url\":\"" + url + "\"}";

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(corsHeaders)
                    .withBody(urlJson);
        }

    }
    
}

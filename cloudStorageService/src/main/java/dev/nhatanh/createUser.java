package dev.nhatanh;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.fileupload.MultipartStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class createUser implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        String tableName = "Users";
        String userID = UUID.randomUUID().toString();

        byte[] body = input.getIsBase64Encoded() ? Base64.getDecoder().decode(input.getBody()) : input.getBody().getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(body);

        String contentType = input.getHeaders().get("content-type");
        int boundaryStartIdx = contentType.indexOf("boundary=");
        String boundary = contentType.substring(boundaryStartIdx + 9);

        MultipartStream ms = new MultipartStream(stream, boundary.getBytes(), 1024, null);
        Map<String, AttributeValue> item = new HashMap<>();
        try {
            boolean hasNextPart = ms.skipPreamble();
            
            while (hasNextPart) {
                String headers = ms.readHeaders();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ms.readBodyData(output);

                int i = headers.indexOf("name=\"");
                int j = headers.indexOf("\"", i + 7);
                String fieldName = headers.substring(i + 6, j);
                item.put(fieldName, AttributeValue.builder().s(output.toString()).build());

                hasNextPart = ms.readBoundary();
            }

        } catch (IOException e) {
            context.getLogger().log("Error skipping preamble: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal Server Error");
        }

        item.put("userID", AttributeValue.builder().s(userID).build());
        item.put("accountCreatedDate", AttributeValue.builder().s(java.time.Instant.now().toString()).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();
        
        ddb.putItem(request);

        context.getLogger().log("User created with ID: " + userID);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        item.remove("password");
        String output = "{";
        for (Entry<String, AttributeValue> entry : item.entrySet()) {
            output += "\"" + entry.getKey() + "\": \"" + entry.getValue().s() + "\",";
        }
        output = output.substring(0, output.length() - 1) + "}" ;
        return response.withHeaders(corsHeaders).withStatusCode(200).withBody(output);

    }
    
}

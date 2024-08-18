package dev.nhatanh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.MultipartStream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class getUser implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        String tableName = "Users";

        byte[] body = input.getIsBase64Encoded() ? Base64.getDecoder().decode(input.getBody()) : input.getBody().getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(body);

        String contentType = input.getHeaders().get("content-type");
        int boundaryStartIdx = contentType.indexOf("boundary=");
        String boundary = contentType.substring(boundaryStartIdx + 9);
        
        MultipartStream ms = new MultipartStream(stream, boundary.getBytes(), 1024, null);
        Map<String, AttributeValue> expressionVals  = new HashMap<>();
        try {
            boolean hasNextPart = ms.skipPreamble();
            
            while (hasNextPart) {
                String headers = ms.readHeaders();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ms.readBodyData(output);

                int i = headers.indexOf("name=\"");
                int j = headers.indexOf("\"", i + 7);
                String fieldName = headers.substring(i + 6, j);
                expressionVals.put(":" + fieldName, AttributeValue.builder().s(output.toString()).build());

                hasNextPart = ms.readBoundary();
            }

        } catch (IOException e) {
            context.getLogger().log("Error skipping preamble: " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal Server Error");
        }

        String filter = "username = :username AND password = :password";
        ScanRequest request = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression(filter)
            .expressionAttributeValues(expressionVals)
            .build();

        List<Map<String, AttributeValue>> response = ddb.scan(request).items();
        if (response.isEmpty()) {
            return new APIGatewayProxyResponseEvent().withStatusCode(404).withBody("User not found");
        }

        Map<String, AttributeValue> user = response.get(0);
        String userJson = "{";
        for (Map.Entry<String, AttributeValue> entry : user.entrySet()) {
            if (entry.getKey() != "password") {
                userJson += "\"" + entry.getKey() + "\": \"" + entry.getValue().s() + "\",";
            }
        }
        userJson = userJson.substring(0, userJson.length() - 1) + "}" ;

        context.getLogger().log("User found with ID: " + user.get("userID").s());

        return new APIGatewayProxyResponseEvent().withStatusCode(200).withHeaders(corsHeaders).withBody(userJson);
    }
    
}

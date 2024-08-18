package dev.nhatanh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.fileupload.MultipartStream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class uploadFile implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        S3Client s3 = S3Client.builder().build();
        String usersTable = "Users";
        String filesTable = "Files";
        String bucketName = "cloud-storage-service";

        //decode form data from body
        byte[] body = input.getIsBase64Encoded() ? Base64.getDecoder().decode(input.getBody()) : input.getBody().getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(body);

        //parse boundary
        String contentType = input.getHeaders().get("content-type");
        int boundaryStartIdx = contentType.indexOf("boundary=");
        String boundary = contentType.substring(boundaryStartIdx + 9);

        MultipartStream ms = new MultipartStream(stream, boundary.getBytes(), 1024, null);
        // store form data here
        Map<String, String> formFields = new HashMap<>();
        Map<String, byte[]> fileUploads = new HashMap<>();
        try {
            boolean hasNextPart = ms.skipPreamble();
            
            while (hasNextPart) {
                String headers = ms.readHeaders();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ms.readBodyData(output);

                if (headers.contains("filename")) {
                    int i = headers.indexOf("filename=\"");
                    int j = headers.indexOf("\"", i + 11);
                    String fileName = headers.substring(i + 10, j);

                    fileUploads.put(fileName, output.toByteArray());
                } else {
                    int i = headers.indexOf("name=\"");
                    int j = headers.indexOf("\"", i + 7);
                    String fieldName = headers.substring(i + 6, j);

                    formFields.put(fieldName, output.toString());
                }

                hasNextPart = ms.readBoundary();
            }

        } catch (IOException e) {
            context.getLogger().log("Error skipping preamble: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(corsHeaders)
                    .withBody("Internal Server Error");
        }

        //upload file to s3
        String fileName = fileUploads.keySet().iterator().next();
        byte[] fileBytes = fileUploads.get(fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(fileName).build();
        RequestBody requestBytes = RequestBody.fromBytes(fileBytes);
        s3.putObject(request, requestBytes);

        context.getLogger().log("File uploaded to S3: " + fileName);
        
        //update files table
        Map<String, AttributeValue> fileItem = new HashMap<>();
        String fileID = java.util.UUID.randomUUID().toString();
        fileItem.put("fileID", AttributeValue.builder().s(fileID).build());
        fileItem.put("userID", AttributeValue.builder().s(formFields.get("userID")).build());
        fileItem.put("fileName", AttributeValue.builder().s(fileName).build());
        fileItem.put("uploadDate", AttributeValue.builder().s(java.time.Instant.now().toString()).build());
        PutItemRequest fileRequest = PutItemRequest.builder().tableName(filesTable).item(fileItem).build();
        ddb.putItem(fileRequest);

        //update users table
        GetItemRequest getUserRequest = GetItemRequest.builder()
                .tableName(usersTable)
                .key(Map.of("userID", AttributeValue.builder().s(formFields.get("userID")).build()))
                .build();

        Map<String, AttributeValue> userItem = ddb.getItem(getUserRequest).item();
        Set<String> updatedAssociatedFiles = new HashSet<>();
        if (userItem.containsKey("associatedFiles")) {
            userItem.get("associatedFiles").ss().forEach(updatedAssociatedFiles::add);
        }
        updatedAssociatedFiles.add(fileID);
        Map<String, AttributeValue> updatedUserItem = new HashMap<>(userItem);
        updatedUserItem.put("associatedFiles", AttributeValue.builder().ss(updatedAssociatedFiles).build());

        PutItemRequest updateUserRequest = PutItemRequest.builder()
                .tableName(usersTable)
                .item(updatedUserItem)
                .build();

        ddb.putItem(updateUserRequest);


        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(corsHeaders)
                .withBody("{ \"msg\": \"File uploaded successfully\" }");
    }
    
}

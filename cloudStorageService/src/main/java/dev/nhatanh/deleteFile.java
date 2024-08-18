package dev.nhatanh;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class deleteFile implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> corsHeaders = Map.of("Access-Control-Allow-Origin", "*");
        DynamoDbClient ddb = DynamoDbClient.builder().build();
        S3Client s3 = S3Client.builder().build();
        String usersTable = "Users";
        String filesTable = "Files";
        String bucketName = "cloud-storage-service";

        String userID = input.getQueryStringParameters().get("userID");
        String fileID = input.getQueryStringParameters().get("fileID");

        //delete from s3
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileID)
                .build();

        s3.deleteObject(request);
        context.getLogger().log("File deleted: " + fileID);

        //delete file entry from files table
        DeleteItemRequest deleteFileRequest = DeleteItemRequest.builder()
                .tableName(filesTable)
                .key(Map.of("fileID", AttributeValue.builder().s(fileID).build()))
                .build();
        ddb.deleteItem(deleteFileRequest);

        GetItemRequest getUserRequest = GetItemRequest.builder()
                .tableName(usersTable)
                .key(Map.of("userID", AttributeValue.builder().s(userID).build()))
                .build();

        //update user entry to remove file from associatedFiles
        Map<String, AttributeValue> userItem = ddb.getItem(getUserRequest).item();
        Set<String> updatedAssociatedFiles = new HashSet<>();
        userItem.get("associatedFiles").ss().forEach(updatedAssociatedFiles::add);
        updatedAssociatedFiles.remove(fileID);
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
                .withBody("File deleted successfully");
    }
    
}

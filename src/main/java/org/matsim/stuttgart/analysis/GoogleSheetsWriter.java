package org.matsim.stuttgart.analysis;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GoogleSheetsWriter implements TabularWriter {

    Logger log = LogManager.getLogger(GoogleSheetsWriter.class);

    private static final String APPLICATION_NAME = "MATSim Google Spreadsheet Writer";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    //private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
   // private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private final String spreadsheetId;
    private final String sheetName;
    private final String[] header;
    private final Path tokenDirectory;
    private final Path credentials;

    public GoogleSheetsWriter(String spreadsheetId, String sheetName, Path tokenDirectory, Path credentials, String[] header) {
        this.spreadsheetId = spreadsheetId;
        this.sheetName = sheetName;
        this.header = header;
        this.tokenDirectory = tokenDirectory;
        this.credentials = credentials;
    }

    @Override
    public void write(List<List<Object>> values) {

        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            Sheets service = new Sheets.Builder(transport, JSON_FACTORY, getCredentials(transport))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // create a new sheet for our values
            var newSheet = new SheetProperties()
                    .setTitle(sheetName);
            BatchUpdateSpreadsheetRequest createSheetsBody = new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(
                            new Request().setAddSheet(new AddSheetRequest().setProperties(newSheet))
                    ));
            service.spreadsheets()
                    .batchUpdate(spreadsheetId, createSheetsBody)
                    .execute();

            // create header body
            var headerRange = new ValueRange()
                    .setValues(List.of(
                            Arrays.asList(header)
                    ))
                    .setRange("'" + sheetName + "'!R1C1:R1C" + header.length);
            // create values body
            var valueRange = new ValueRange()
                    .setValues(values)
                    // this sets the range to the number of rows from the input and the number of columns of the header
                    .setRange("'" + sheetName + "'!R2C1:R" + (values.size() + 1) + "C" + header.length);
            // form the request to update all at once
            BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
                    .setValueInputOption("RAW")
                    .setData(List.of(headerRange, valueRange));

            // make the call to insert values
            BatchUpdateValuesResponse response = service.spreadsheets().values()
                    .batchUpdate(spreadsheetId, body)
                    .execute();

            log.info("Successfully wrote to sheet id: " + spreadsheetId);


        } catch (Exception e) {
            // catch any error, give feedback but try to go on because other writers might succeed still.
            log.error("Failed to write to " + spreadsheetId + " Reason: ");
            log.error(e.getMessage());
            log.error(e);

        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = Files.newInputStream(credentials);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory.toFile()))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
}

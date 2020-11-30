package com.fg.util.babylon.gsheet;

import com.fg.util.babylon.legacy.GoogleSheetApi;
import com.fg.util.babylon.sheets.GoogleSheetContract;
import com.fg.util.babylon.sheets.SheetsException;
import com.google.api.services.sheets.v4.model.Sheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

// migrating from GoogleSheetApi to LightGoogleSheetService
public class LegacyGoogleSheetApiAdaptor implements GoogleSheetContract {

    private final LightGoogleSheetService lightGoogleSheetService;
    private final GoogleSheetApi googleSheetApi;

    public LegacyGoogleSheetApiAdaptor(LightGoogleSheetService lightGoogleSheetService,
                                       GoogleSheetApi googleSheetApi) {
        this.lightGoogleSheetService = lightGoogleSheetService;
        this.googleSheetApi = googleSheetApi;
    }

    @Override
    public List<Sheet> listSheets(String spreadsheetId) throws SheetsException {
        try {
            return googleSheetApi.getAllSheets(spreadsheetId);
        } catch (GeneralSecurityException | IOException e) {
            throw new SheetsException("Error when listing all sheets of spreadsheet '" + spreadsheetId + "'", e);
        }
    }

    @Override
    public void deleteSheets(String spreadsheetId, Iterable<Integer> sheetIds) throws SheetsException {
        try {
            googleSheetApi.deleteSheets(spreadsheetId, sheetIds);
        } catch (IOException | GeneralSecurityException e) {
            String errMsg = "Error when deleting sheets '" + sheetIds + "' of spreadsheet '" + spreadsheetId + "'";
            throw new SheetsException(errMsg, e);
        }
    }

    @Override
    public void uploadDataToGoogleSheet(String spreadsheetId, String sheetTitle, List<List<String>> sheetRows, List<String> lockedCellEditors) throws SheetsException {
        try {
            Sheet existingSheet = lightGoogleSheetService.loadSheet(spreadsheetId, sheetTitle);
            if (existingSheet != null) {
                throw new SheetsException("Sheet '" + sheetTitle + "' already exists.");
            }
            lightGoogleSheetService.uploadDataToGoogleSheet(spreadsheetId, sheetTitle, sheetRows);
            Sheet sheet = lightGoogleSheetService.loadSheet(spreadsheetId, sheetTitle);
            Integer sheetId = sheet.getProperties().getSheetId();
            lightGoogleSheetService.updateSheetStyle(spreadsheetId, sheetId, lockedCellEditors);
        } catch (IOException | GeneralSecurityException e) {
            String errMsg = "Error when creating sheet '" + sheetTitle + "' in spreadsheet '" + spreadsheetId + "'";
            throw new SheetsException(errMsg, e);
        }
    }

}

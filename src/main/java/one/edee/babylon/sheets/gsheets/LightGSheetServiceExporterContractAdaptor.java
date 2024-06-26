package one.edee.babylon.sheets.gsheets;

import one.edee.babylon.export.Exporter;
import one.edee.babylon.sheets.gsheets.model.ASheet;
import one.edee.babylon.sheets.gsheets.model.SheetAdaptor;
import one.edee.babylon.sheets.SheetsException;
import com.google.api.services.sheets.v4.model.Sheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements contract for translation export using {@link LightGSheetService} without exposing its details.
 */
public class LightGSheetServiceExporterContractAdaptor implements Exporter.SheetContract {

    private final LightGSheetService lightGSheetService;

    public LightGSheetServiceExporterContractAdaptor(LightGSheetService lightGSheetService) {
        this.lightGSheetService = lightGSheetService;
    }

    @Override
    public List<ASheet> listSheets(String spreadsheetId) throws SheetsException {
        try {
            return lightGSheetService.listSheetsLazily(spreadsheetId).stream()
                    .map(SheetAdaptor::new)
                    .collect(Collectors.toList());
        } catch (GeneralSecurityException | IOException e) {
            throw new SheetsException("Error when listing all sheets of spreadsheet '" + spreadsheetId + "'", e);
        }
    }

    @Override
    public void deleteSheets(String spreadsheetId, Collection<Integer> sheetIds) throws SheetsException {
        try {
            lightGSheetService.deleteSheets(spreadsheetId, sheetIds);
        } catch (IOException | GeneralSecurityException e) {
            String errMsg = "Error when deleting sheets '" + sheetIds + "' of spreadsheet '" + spreadsheetId + "'";
            throw new SheetsException(errMsg, e);
        }
    }

    @Override
    public void createSheet(String spreadsheetId, String sheetTitle, List<List<String>> sheetRows, List<String> lockedCellEditors, Map<String, List<String>> changed) throws SheetsException {
        try {
            Sheet existingSheet = lightGSheetService.loadSheet(spreadsheetId, sheetTitle);
            if (existingSheet != null) {
                throw new SheetsException("Sheet '" + sheetTitle + "' already exists.");
            }
            lightGSheetService.uploadDataToGoogleSheet(spreadsheetId, sheetTitle, sheetRows);
            Sheet sheet = lightGSheetService.loadSheet(spreadsheetId, sheetTitle);
            Integer sheetId = sheet.getProperties().getSheetId();
            lightGSheetService.updateSheetStyle(spreadsheetId, sheetTitle, sheetId, lockedCellEditors, changed);
        } catch (IOException | GeneralSecurityException e) {
            String errMsg = "Error when creating sheet '" + sheetTitle + "' in spreadsheet '" + spreadsheetId + "'";
            throw new SheetsException(errMsg, e);
        }
    }

}

package one.edee.babylon.sheets.gsheets;

import com.google.api.services.sheets.v4.model.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * Helps create requests for the Google Sheets API client library.
 */
public class GSheetApiRequestFactory {

    public Request addSheet(String newSheetTitle,
                            Integer rowCount, Integer colCount,
                            Integer rowsToFreeze, Integer colsToFreeze) {
        return new Request()
                .setAddSheet(doAddSheet(newSheetTitle, rowCount, colCount, rowsToFreeze, colsToFreeze));
    }

    /**
     * Adds a new sheet. A sheet with this name should not exist in the spreadsheet!
     */
    private AddSheetRequest doAddSheet(String newSheetTitle,
                                       Integer rowCount, Integer colCount,
                                       Integer rowsToFreeze, Integer colsToFreeze) {

        GridProperties gridProperties = new GridProperties()
                .setRowCount(rowCount)
                .setColumnCount(colCount)
                .setFrozenRowCount(rowsToFreeze)
                .setFrozenColumnCount(colsToFreeze);

        SheetProperties sheetProperties = new SheetProperties()
                .setTitle(newSheetTitle)
                .setGridProperties(gridProperties);

        return new AddSheetRequest()
                .setProperties(sheetProperties);
    }

    public Request setWrapWrappingStrategyForAllCells(Integer sheetId) {
        return new Request()
                .setRepeatCell(wrappingStrategyCellUpdate(allCellsRange(sheetId)));
    }

    private RepeatCellRequest wrappingStrategyCellUpdate(GridRange gridRange) {
        return new RepeatCellRequest()
                .setFields("userEnteredFormat.wrapStrategy")
                .setCell(new CellData()
                        .setUserEnteredFormat(new CellFormat()
                                .setWrapStrategy("WRAP")))
                .setRange(gridRange);
    }

    private GridRange allCellsRange(Integer sheetId) {
        return new GridRange()
                .setSheetId(sheetId)
                .setStartRowIndex(0)
                .setStartColumnIndex(0);
    }

    /**
     * Resizes all columns in given sheet
     *
     * @param sheetId   id of the sheet
     * @param pixelSize size of pixels to resize columns to
     * @return API request
     */

    public Request resizeAllColumns(Integer sheetId, Integer pixelSize) {
        return new Request()
                .setUpdateDimensionProperties(
                        resizeDimension(allColumns(sheetId), pixelSize));
    }

    private UpdateDimensionPropertiesRequest resizeDimension(DimensionRange dimensionRange, Integer pixelSize) {
        return new UpdateDimensionPropertiesRequest()
                .setRange(dimensionRange)
                .setFields("pixelSize")
                .setProperties(new DimensionProperties()
                        .setPixelSize(pixelSize));
    }

    public Request hideFirstColumn(Integer sheetId) {
        return new Request()
                .setUpdateDimensionProperties(
                        hideDimension(firstColumn(sheetId)));
    }

    private UpdateDimensionPropertiesRequest hideDimension(DimensionRange dimensionRange) {
        return new UpdateDimensionPropertiesRequest()
                .setRange(dimensionRange)
                .setFields("hiddenByUser")
                .setProperties(new DimensionProperties()
                        .setHiddenByUser(Boolean.TRUE));
    }

    private static final String DIMENSION_RANGE__DIMENSION__COLUMNS = "COLUMNS";

    private DimensionRange allColumns(Integer sheetId) {
        return new DimensionRange()
                .setSheetId(sheetId)
                .setDimension(DIMENSION_RANGE__DIMENSION__COLUMNS);
    }

    private DimensionRange firstColumn(Integer sheetId) {
        return new DimensionRange()
                .setSheetId(sheetId)
                .setDimension(DIMENSION_RANGE__DIMENSION__COLUMNS)
                .setStartIndex(0)
                .setEndIndex(1);
    }

    public Request protectCellsInFirstTwoColumns(Integer sheetId, List<String> lockedCellEditors) {
        return new Request()
                .setAddProtectedRange(
                        protectCells(firstTwoColsCellsRange(sheetId), lockedCellEditors));
    }

    private AddProtectedRangeRequest protectCells(GridRange gridRange, List<String> lockedCellEditors) {
        return new AddProtectedRangeRequest()
                .setProtectedRange(lockCells(gridRange, lockedCellEditors));
    }

    /**
     * @param gridRange      range of cells to lock
     * @param editorAccounts accounts allowed to edit locked cells
     */
    private ProtectedRange lockCells(GridRange gridRange, List<String> editorAccounts) {
        return new ProtectedRange()
                .setRange(gridRange)
                .setRequestingUserCanEdit(false)
                .setWarningOnly(false)
                .setEditors(new Editors()
                        .setUsers(editorAccounts));
    }

    private GridRange firstTwoColsCellsRange(Integer sheetId) {
        return new GridRange()
                .setSheetId(sheetId)
                .setStartColumnIndex(0)
                .setEndColumnIndex(2)
                .setStartRowIndex(0);
    }

    public Request deleteSheet(Integer sheetId) {
        return new Request()
                .setDeleteSheet(deleteSheetRequest(sheetId));
    }

    private DeleteSheetRequest deleteSheetRequest(Integer sheetId) {
        return new DeleteSheetRequest()
                .setSheetId(sheetId);
    }

    public List<Request> changeCellColor(Integer sheetId, String sheetTitle, Map<String, List<String>> changed) {
        List<String> changes = changed.get(sheetTitle);
        List<Request> reqs = new LinkedList<>();
        if (changes != null && !changes.isEmpty()) {
            for (String change : changes) {
                String[] s = change.split("_");
                int row = Integer.parseInt(s[0]);
                int column = Integer.parseInt(s[1]);

                reqs.add(
                        new Request()
                                .setUpdateCells(
                                        new UpdateCellsRequest()
                                                .setRange(
                                                        new GridRange()
                                                                .setSheetId(sheetId)
                                                                .setStartColumnIndex(column)
                                                                .setEndColumnIndex(column + 1)
                                                                .setStartRowIndex(row)
                                                                .setEndRowIndex(row + 1)
                                                )
                                                .setRows(
                                                        singletonList(
                                                                new RowData()
                                                                        .setValues(
                                                                                singletonList(
                                                                                        new CellData()
                                                                                                .setUserEnteredFormat(
                                                                                                        new CellFormat()
                                                                                                                .setBackgroundColor(
                                                                                                                        new Color()
                                                                                                                                .setRed(1f)
                                                                                                                                .setGreen(0.8f)
                                                                                                                                .setBlue(0.61f)
                                                                                                                )
                                                                                                )
                                                                                )
                                                                        )
                                                        )
                                                )
                                                .setFields("userEnteredFormat.backgroundColor")
                                )
                );
            }
        }
        return reqs;
    }
}

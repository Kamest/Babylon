package com.fg.util.babylon.processor;


import com.fg.util.babylon.SheetConstants;
import com.fg.util.babylon.db.DataFileManager;
import com.fg.util.babylon.entity.*;
import com.fg.util.babylon.enums.Action;
import com.fg.util.babylon.enums.PropertyStatus;
import com.fg.util.babylon.enums.PropertyType;
import com.fg.util.babylon.exception.SheetExistsException;
import com.fg.util.babylon.propfiles.PropertyFileActiveRecord;
import com.fg.util.babylon.propfiles.Property;
import com.fg.util.babylon.service.GoogleSheetService;
import com.fg.util.babylon.statistics.ExportFileStatistic;
import com.fg.util.babylon.statistics.TranslationStatisticsOfExport;
import com.fg.util.babylon.todo.TranslationFileUtils;
import com.fg.util.babylon.util.JsonUtils;
import com.fg.util.babylon.util.PathUtils;
import com.fg.util.babylon.util.SheetUtils;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Sheet;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Processor for {@link Action#EXPORT} action.
 * @author Tomas Langer (langer@fg.cz), FG Forrest a.s. (c) 2019
 */
@CommonsLog
public class ExportProcessor {

    private final DataFileManager dataFileManager;
    private final AntPathResourceLoader resourceLoader;
    private final I18nFileManager i18nFileManager;
    private final GoogleSheetService googleSheetService;

    private final String googleSheetId;
    private final TranslationConfiguration configuration;

    protected TranslationStatisticsOfExport statistics;

    public ExportProcessor(GoogleSheetService googleSheetService,
                           DataFileManager dataFileManager,
                           I18nFileManager i18nFileManager,
                           AntPathResourceLoader resourceLoader,
                           String googleSheetId,
                           TranslationConfiguration configuration) {
        this.dataFileManager = dataFileManager;
        this.resourceLoader = resourceLoader;
        this.i18nFileManager = i18nFileManager;
        this.googleSheetService = googleSheetService;
        this.googleSheetId = googleSheetId;
        this.configuration = configuration;
    }

    public void doExport() throws IOException, GeneralSecurityException {
        log.info("Started translation EXPORT with Google sheet id: '" + googleSheetId +"'");
        statistics = new TranslationStatisticsOfExport();
        statistics.setAction(Action.EXPORT);

        List<String> changedMessageFilePaths = new LinkedList<>();
        // Using "for" loop to propagating of IOException
        for (String path : configuration.getPath()) {
            processPath(path, changedMessageFilePaths);
        }
        uploadDataToGoogleSpreadsheet(changedMessageFilePaths);
        saveSnapshotWithoutProperties();
        log.info(statistics);
    }

    /**
     * Processing of one language property path (primary language properties files and its translation files).
     * @param path path to one or more primary properties files.
     * @throws IOException some exception derived from {@link IOException}
    */
    private void processPath(String path, List<String> changedMessageFilePaths) throws IOException {
        List<String> allPaths = new PathUtils().expandPath(path, resourceLoader);

        final String TRANSLATION_FILES_REGEX = ".*_[a-zA-Z]{2,3}\\.properties";
        // Filter out possible mutations properties files, because we only need primary language property files
        allPaths.removeIf(item -> item.matches(TRANSLATION_FILES_REGEX));

        log.info("Processing property files: ");
        allPaths.forEach(log::info);
        statistics.incPrimaryPropFilesProcessed(allPaths.size()); //FIXME: should be called after the file has really been processed
        // Process all properties of all files.
        for (String pathToMsgFile : allPaths) {
            MessageFileContent primaryMessageFileContent = dataFileManager.getOrCreateDataFile().getOrPutNewPropFileByFileName(pathToMsgFile);
            processPrimaryMessages(pathToMsgFile, primaryMessageFileContent, changedMessageFilePaths);
        }
    }

    private void processPrimaryMessages(String pathToMsgFile, MessageFileContent primaryMessageFileContent, List<String> changedMessageFilePaths) throws IOException {
        PropertyFileActiveRecord primaryMessages = i18nFileManager.loadPropertiesFromFile(pathToMsgFile);
        if (primaryMessages == null) {
            throw new FileNotFoundException("Primary language message file: " + pathToMsgFile + " does not exist.");
        }
        statistics.incTotalPropFilesProcessed(); //FIXME: should be called after the file has really been processed

        changedMessageFilePaths.add(pathToMsgFile);
        for (Map.Entry<String, Property> entry : primaryMessages.entrySet()) {
            String msgKey = entry.getKey();
            Property message = entry.getValue();
            // Skip processing of comments and empty lines, process only simple or multiline key=value values.
            if (!message.isPropValue() && !message.isPropValueMultiLine()) {
                continue;
            }
            // Compares value to that key value within a given file with the value stored in the DataPropFile object read from Json data file.
            // - Keys which not found in Json data file is marked as NEW.
            // - Keys which value is different from value in Json data file is marked as CHANGED.
            primaryMessageFileContent.putProperty(msgKey, message.getValue());
        }
        List<String> deprecatedProperties = new LinkedList<>();
        primaryMessageFileContent.getProperties().forEach((i, j)->{

            Property property = primaryMessages.get(i);
            if (property == null){
                deprecatedProperties.add(i);
            }
        });
        deprecatedProperties.forEach(i-> primaryMessageFileContent.getProperties().remove(i));

        Map<String, PropertyFileActiveRecord> translations = loadTranslationProperties(pathToMsgFile);
        for (Map.Entry<String, Property> entry : primaryMessages.entrySet()) {
            // Checks that the key exists in secondary mutation files (or that there are no secondary mutations)
            processSecondaryMutations(entry.getKey(), pathToMsgFile, translations, primaryMessageFileContent);
        }
    }

    /**
     * Method loads all properties from translation files for given primary language file.
     * @return returns map where key is mutation and value is properties loaded from translation file.
     */
    private Map<String, PropertyFileActiveRecord> loadTranslationProperties(String primaryPropertyFilePath) throws IOException {
        Map<String, PropertyFileActiveRecord> map = new HashMap<>();
        for (String language : configuration.getMutations()) {
            String translationFileNamePath = TranslationFileUtils.getFileNameForTranslation(primaryPropertyFilePath, language);
            PropertyFileActiveRecord properties = Optional.ofNullable(i18nFileManager.loadPropertiesFromFile(translationFileNamePath)).orElse(new PropertyFileActiveRecord());
            if (!properties.isEmpty()) {
                statistics.incTotalPropFilesProcessed();
            }
            map.put(language, properties);
        }
        return map;
    }

    /**
     * Processing all defined secondary mutations of key, defined in configuration for given primary properties file.
     * @param key primary property key
     * @param primaryPropFilePath path to the primary mutation file
     * @param filesMutationProps map with all properties from secondary mutation files
     * @param primaryMessageFileContent data of primary property file
     */
    private void processSecondaryMutations(String key,
                                           String primaryPropFilePath,
                                           Map<String, PropertyFileActiveRecord> filesMutationProps,
                                           MessageFileContent primaryMessageFileContent) {
        PropertyStatus primaryPropStatus = primaryMessageFileContent.getPropertyStatus(key);
        PropertiesMap mutationPropsMap;
        for (String mutation : configuration.getMutations()) {
            log.debug("Processing key \"" + key + "\" for mutation \"" + mutation + "\" of \"" + primaryPropFilePath + "\"");
            // Get all properties for secondary mutation.
            final PropertyFileActiveRecord properties = Optional.ofNullable(filesMutationProps.get(mutation)).orElse(new PropertyFileActiveRecord());
            // Get value of property from existing mutation properties file or set empty value if property not found.
            Property propValue = Optional.ofNullable(properties.get(key)).orElse(new Property(PropertyType.VALUE, SheetConstants.EMPTY_VAL));
            mutationPropsMap = getMutationPropertiesMap(primaryMessageFileContent, mutation);
            // Default status of mutation property is UNCHANGED.
            mutationPropsMap.putPropertyStatus(key, PropertyStatus.UNCHANGED);
            // Set default value from properties file
            mutationPropsMap.put(key, propValue.getValue());
            /* Resolves final status of the property value. If its status is not PropertyStatus.UNCHANGED then property will be exported. */
            // If property doesn't exists in file of secondary mutation or file for secondary mutation doesn't exists...
            if (properties.get(key) == null) {
                // set its value to empty string and status to MISSING.
                mutationPropsMap.put(key, SheetConstants.EMPTY_VAL, PropertyStatus.MISSING);
            } else if(primaryPropStatus == PropertyStatus.CHANGED) {
                mutationPropsMap.put(key, SheetConstants.EMPTY_VAL, PropertyStatus.CHANGED);
            } else {
                /* Otherwise compare key value from actual DataFile from data.json file on disk.
                   This covers scenarios:
                   - new property in primary mutation file (key not exists in Json DataFile) -> status = NEW
                   - changes of property value in primary mutation file -> status = CHANGED, propValue = ""
                   (secured in DataPropFile#putProperty(String key, String value) method)
                 */
                MessageFileContent propFileByFileName = null;
                // No json datafile exists on disk or no record for this file in Json DataFile on disk -> NEW
                if (dataFileManager.getOriginalDataFile() != null) { // FIXME: this can never happen, remove branch
                    propFileByFileName = dataFileManager.getOriginalDataFile().getPropFileByFileName(primaryPropFilePath);
                }
                if (propFileByFileName == null) {
                    mutationPropsMap.putPropertyStatus(key, PropertyStatus.NEW);
                } else {
                    // Value from json DataPropFile from disk.
                    String propVal = propFileByFileName.getProperties().get(key);
                    // Value is missing in json data file -> NEW
                    if (propVal == null ) {
                        mutationPropsMap.putPropertyStatus(key, PropertyStatus.NEW);
                    } else {
                        // Value in primary properties file is changed (against stored value in json DataFile) ->
                        // all secondary mutations must be translated again.
                        String primaryPropVal = primaryMessageFileContent.getPropertyValue(key);
                        if (!propVal.equals(primaryPropVal)) {
                            mutationPropsMap.put(key, SheetConstants.EMPTY_VAL, PropertyStatus.CHANGED);
                        }
                    }
                }
            }
            String mutationPropFilePath = TranslationFileUtils.getFileNameForTranslation(primaryPropFilePath, mutation);
            countStatistics(key, mutationPropsMap, mutationPropFilePath);
        }
        /* Set final primary properties status by statuses of this key in all secondary properties.
          - if all secondary properties for this key have status UNCHANGED then set primary property status to UNCHANGED
          - if at least one secondary properties status is not UNCHANGED then set primary property status to CHANGED
         */
        boolean allUnchanged = primaryMessageFileContent.getMutationProperties().values().stream().allMatch(propertiesMap ->
                propertiesMap.getPropertiesStatus().entrySet().stream()
                        .filter(entry -> entry.getKey().equals(key))
                        .allMatch(entry -> entry.getValue() == PropertyStatus.UNCHANGED)
        );
        primaryMessageFileContent.putPropertyStatus(key, allUnchanged ? PropertyStatus.UNCHANGED : PropertyStatus.CHANGED);
    }

    private PropertiesMap getMutationPropertiesMap(MessageFileContent primaryMessageFileContent, String mutation) {
        PropertiesMap mutationPropsMap;
        mutationPropsMap = primaryMessageFileContent.getMutationProperties(mutation);
        if (mutationPropsMap == null) {
            mutationPropsMap = new PropertiesMap();
            primaryMessageFileContent.putMutationProperties(mutation, mutationPropsMap);
        }
        return mutationPropsMap;
    }

    /**
     * Final Re-count of statistics by final status of property.
     * @param key key of property
     * @param mutationPropsMap map of mutation properties
     * @param mutationPropFilePath relative path to the mutation property file
     */
    private void countStatistics(String key, PropertiesMap mutationPropsMap, String mutationPropFilePath) {
        PropertyStatus primaryPropStatus;
        ExportFileStatistic fileStatistic = statistics.getFileStatistic(mutationPropFilePath);
        if (fileStatistic == null) {
            fileStatistic = new ExportFileStatistic();
            statistics.putFileStatistic(mutationPropFilePath, fileStatistic);
        }
        primaryPropStatus = mutationPropsMap.getPropertyStatus(key);
        if (primaryPropStatus == PropertyStatus.NEW) {
            fileStatistic.incNewKeysCnt();
            statistics.incTotalNewKeysCnt();
        } else if (primaryPropStatus == PropertyStatus.CHANGED) {
            fileStatistic.incKeysToUpdateCnt();
            statistics.incTotalKeysToUpdateCnt();
        } else if (primaryPropStatus == PropertyStatus.MISSING) {
            fileStatistic.incMissingKeysTranslationCnt();
            statistics.incTotalMissingKeysTranslationCnt();
        }
        fileStatistic.incTotalKeysCnt();
    }

    /**
     * Uploads data {@link DataFileManager#getOrCreateDataFile()} into google spreadsheet specified by {@link Arguments#getGoogleSheetId()}.
     * @throws GeneralSecurityException when authentication to Google sheets API problem is appeared.
     * @throws IOException some exception derived from {@link IOException}
    */
    private void uploadDataToGoogleSpreadsheet(List<String> changedMessageFilePaths) throws GeneralSecurityException, IOException {
        Map<String, MessageFileContent> messageBundle = dataFileManager.getOrCreateDataFile().getDataPropFiles();
        // FIXME: It would be more efficient to iterate over the identifiers of the List and look them up in the Map
        Map<String, MessageFileContent> changedMessages = messageBundle
                .entrySet()
                .stream()
                .filter(i -> changedMessageFilePaths.contains(i.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // FIXME: why atomic?
        AtomicInteger processedCount = new AtomicInteger(0);
        // Gets all sheets existing at this moment.
        List<Sheet> prevAllSheets = googleSheetService.getAllSheets(googleSheetId);
        for (Map.Entry<String, MessageFileContent> entry : changedMessages.entrySet()) {
            String fileNamePath = entry.getKey();
            MessageFileContent messageFileContent = entry.getValue();

            uploadDataToGoogleSheet(messageFileContent, fileNamePath, processedCount);
        }
        // Delete all previously existing sheets (usually default "Sheet 1" of new empty spreadsheet) if
        // current sheets count is greater then previous.
        List<Sheet> currAllSheets = googleSheetService.getAllSheets(googleSheetId);
        if (currAllSheets.size() > prevAllSheets.size()) {
            googleSheetService.deleteSheets(googleSheetId, prevAllSheets);
        }
    }

    private Sheet createGoogleSheet(List<List<Object>> sheetRows, String sheetTitle) throws GeneralSecurityException, IOException {
        Integer columnCount = sheetRows.get(0).size();
        Integer rowCount = sheetRows.size();
        SheetParams sheetParams = new SheetParams(sheetTitle, columnCount, rowCount);
        // Freezes first row (header) to prevent scrolling of this row with rest of data.
        if (rowCount > 1) {
            sheetParams.setFrozenRowCount(1);
            sheetParams.setFrozenColumnCount(2);
        }
        return googleSheetService.addSheet(googleSheetId, sheetParams);
    }

    private void uploadDataToGoogleSheet(MessageFileContent messageFileContent, String fileNamePath, AtomicInteger processedCount) throws IOException, GeneralSecurityException {
        // Add header into sheet
        List<List<Object>> sheetRows = new LinkedList<>(createSheetHeader());
        // Add data into sheet
        List<List<Object>> sheetData = createSheetData(messageFileContent);

        // If no data to upload return
        if (sheetData.isEmpty()) {
            log.info("No changed data for primary properties file and its mutation files: " + fileNamePath);
            return;
        }

        pauseProcessIfGoogleLimitExceed(sheetData.size(),processedCount);

        // Title of target google sheet is created from "properties fileName only" + "#" + "fileName id".
        String sheetTitle = new SheetUtils().getSheetName(fileNamePath, messageFileContent);
        log.info("Uploading data of \"" + fileNamePath + "\" into google sheet \"" + sheetTitle + "\"...");
        sheetRows.addAll(sheetData);
        Sheet sheet = createGoogleSheet(sheetRows, sheetTitle);
        if (sheet != null) {
            throw new SheetExistsException("Sheet \"" + sheetTitle + "\" already exists!");
        }

        googleSheetService.writeDataIntoSheet(googleSheetId, sheetTitle, sheetRows);
        sheet = googleSheetService.getSheet(googleSheetId, sheetTitle);
        googleSheetService.setWrappingStrategy(googleSheetId,sheet.getProperties().getSheetId());
        googleSheetService.resizeAllColumns(googleSheetId, sheet.getProperties().getSheetId());
        googleSheetService.protectFirstColumns(googleSheetId, sheet.getProperties().getSheetId());
        hideSheetFirstColumn(sheet.getProperties().getSheetId());
    }

    private void pauseProcessIfGoogleLimitExceed(int size, AtomicInteger processedCount) {
        // This sleep is cause of google limit where user cannot have more than 500 request in less than 100 secs
        // *2 is for updating styles :(
        int count = processedCount.addAndGet(size);
        // 300 is minus average keys that updates as frozen and protected cols
        if (count > 300){
            try {
                log.info("Google has it's limits I have to go to bed for about two minutes, so sorry :( .");
                Thread.sleep(120*1000L);
                processedCount.set(0);
            }catch (Exception e){
                // do nothing
            }
        }
    }

    private List<List<Object>> createSheetHeader() {
        List<List<Object>> sheetHeader = new LinkedList<>();
        List<Object> headerValues = new LinkedList<>(Arrays.asList(SheetConstants.COL_KEY, SheetConstants.COL_PRIMARY));
        headerValues.addAll(configuration.getMutations());
        sheetHeader.add(headerValues);
        return sheetHeader;
    }

    private List<List<Object>> createSheetData(MessageFileContent messageFileContent) {
        List<List<Object>> sheetData = new LinkedList<>();
        for (Map.Entry<String, String> entry : messageFileContent.getProperties().entrySet()) {
            // Add row only if status != UNCHANGED
            PropertyStatus propertyStatus = messageFileContent.getPropertyStatus(entry.getKey());
            if (propertyStatus == PropertyStatus.UNCHANGED) {
                continue;
            }
            // Replace doubled quotes in case of variable in property
            String entryValue = entry.getValue();
            if (entryValue.matches(".*\\{.}.*")){
                entryValue = entryValue.replace("''","'");
            }

            // Add key name and primary mutation value
            List<Object> rowValues = new LinkedList<>(Arrays.asList(entry.getKey(), entryValue));
            // Add all secondary mutations values
            for (String mutation : configuration.getMutations()) {
                PropertiesMap mutationsPropsMap = messageFileContent.getMutationProperties(mutation);
                if (mutationsPropsMap == null) {
                    mutationsPropsMap = new PropertiesMap();
                    messageFileContent.putMutationProperties(mutation, mutationsPropsMap);
                }
                String mutationValue = mutationsPropsMap.get(entry.getKey());
                // Replace doubled quotes in case of variable in property
                if (mutationValue != null && mutationValue.matches(".*\\{.}.*")) {
                    mutationValue = mutationValue.replace("''", "'");
                }

                rowValues.add(mutationValue);
            }
            sheetData.add(rowValues);
        }
        return sheetData;
    }

    /**
     * Hiding of first column which contains properties keys, because it's not important for workers in translation agency.
     * @param sheetId ID of the target sheet
     * @throws IOException some exception derived from {@link IOException}
     * @throws GeneralSecurityException when authentication to Google sheets API problem is appeared.
     */
    private void hideSheetFirstColumn(Integer sheetId) throws IOException, GeneralSecurityException {
        DimensionRange dimensionRange = new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("COLUMNS")
                .setStartIndex(0)
                .setEndIndex(1);
        googleSheetService.hideDimensionRange(googleSheetId, dimensionRange);
    }

    /**
     * Saves created Snapshot object without properties into file on disk. Only if DataFile not exists on disk!
     * FIXME: tohle prida do snapshotu cesty k novym message souborum, stavajici to ponecha
     * FIXME: kdyz budu mit seznam novych souboru, nemusim tohle delat
     */
    private void saveSnapshotWithoutProperties() throws IOException {
        Map<String, MessageFileContent> originalDataPropFiles = dataFileManager.getOriginalDataFile().getDataPropFiles();

        Snapshot overriddenSnapshot = dataFileManager.getOrCreateDataFile();
        List<String> newExportedFiles = overriddenSnapshot.getDataPropFiles()
                .keySet()
                .stream()
                .filter(exportedFilePath -> !originalDataPropFiles.containsKey(exportedFilePath))
                .collect(Collectors.toList());
        updateSnapshotWithNewFilePaths(newExportedFiles);
    }

    private void updateSnapshotWithNewFilePaths(Iterable<String> newMsgFiles) throws IOException {
        Snapshot untouchedSnapshot = dataFileManager.getOriginalDataFile();
        MessageFileContent emptyContent = new MessageFileContent();
        newMsgFiles.forEach(newMsgFile ->
                untouchedSnapshot.putPropFile(newMsgFile, emptyContent)
        );
        File snapshotFileName = new File(configuration.getDataFileName());
        JsonUtils.objToJsonFile(snapshotFileName, dataFileManager.getOriginalDataFile(), true);
    }

}

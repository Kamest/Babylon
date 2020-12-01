package com.fg.util.babylon.sheets;

import com.fg.util.babylon.SheetConstants;
import com.fg.util.babylon.entity.MessageFileContent;
import com.fg.util.babylon.entity.PropertiesMap;
import com.fg.util.babylon.enums.PropertyStatus;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;

import java.util.*;

/**
 * Deals with sheets, but outside of Google Sheets API. Only prepares data. Soon to be deprecated.
 */
public class SheetUtils {

    public String getSheetName(String msgFilePath, Integer msgFileId) {
        return FilenameUtils.getBaseName(msgFilePath) + "#" + msgFileId;
    }

    public String getSheetName(String msgFilePath, MessageFileContent msgFileContent) {
        return getSheetName(msgFilePath, msgFileContent.getId());
    }

    public List<String> createSheetHeader(List<String> translationLangs) {
        List<String> headerValues = new ArrayList<>();
        headerValues.addAll(Arrays.asList(SheetConstants.COL_KEY, SheetConstants.COL_PRIMARY));
        headerValues.addAll(translationLangs);
        return headerValues;
    }

    public List<List<String>> createSheetData(MessageFileContent messageFileContent, List<String> translationLangs) {
        List<List<String>> sheetData = new LinkedList<>();
        for (Map.Entry<String, String> entry : messageFileContent.getProperties().entrySet()) {
            // Add row only if status != UNCHANGED
            PropertyStatus propertyStatus = messageFileContent.getPropertyStatus(entry.getKey());
            if (propertyStatus == PropertyStatus.UNCHANGED) {
                continue;
            }
            // Replace doubled quotes in case of variable in property
            String entryValue = entry.getValue();
            if (entryValue.matches(".*\\{.}.*")) {
                entryValue = entryValue.replace("''", "'");
            }

            // Add key name and primary mutation value
            List<String> rowValues = new LinkedList<>(Arrays.asList(entry.getKey(), entryValue));
            // Add all secondary mutations values
            for (String mutation : translationLangs) {
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

}
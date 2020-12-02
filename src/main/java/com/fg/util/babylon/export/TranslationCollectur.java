package com.fg.util.babylon.export;

import com.fg.util.babylon.export.data.ExportResult;
import com.fg.util.babylon.export.data.TranslationSheet;
import com.fg.util.babylon.sheets.SheetUtils;
import com.fg.util.babylon.snapshot.TranslationSnapshotReadContract;
import com.fg.util.babylon.snapshot.TranslationSnapshotWriteContract;
import lombok.extern.apachecommons.CommonsLog;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects messages from primary language message files and from translation message files.
 */
@CommonsLog
public class TranslationCollectur {

    private MessageLoader messageLoader;
    private MessageFileProcessor messageFileProcessor;
    private TranslationSnapshotReadContract snapshotReadContract;
    private TranslationSnapshotWriteContract snapshotWriteContract;

    public TranslationCollectur(MessageLoader messageLoader,
                                MessageFileProcessor messageFileProcessor,
                                TranslationSnapshotReadContract snapshotReadContract,
                                TranslationSnapshotWriteContract snapshotWriteContract) {
        this.messageLoader = messageLoader;
        this.messageFileProcessor = messageFileProcessor;
        this.snapshotReadContract = snapshotReadContract;
        this.snapshotWriteContract = snapshotWriteContract;
    }

    /**
     * Collects message files and given a list of languages to translate to, generates a translation sheet
     * that contains messages in primary language and possibly existing translated messages, if the translated message
     * exists for given language and unless invalidated by the change to its primary message.
     *
     * @param allPaths    paths to message files
     * @param translateTo list of languages to translate to
     */
    public ExportResult walkPathsAndCollectTranslationSheets(Collection<String> allPaths,
                                                             List<String> translateTo) {
        List<String> newMsgFilesPaths = allPaths.stream()
                .filter(msgFilePath -> !snapshotReadContract.includesMsgFile(msgFilePath))
                .collect(Collectors.toList());

        List<TranslationSheet> sheets = allPaths.stream()
                .map(msgFilePath -> processMsgFile(msgFilePath, translateTo))
                .collect(Collectors.toList());

        //FIXME: move up?
        List<String> obsoleteFilePaths = snapshotReadContract.listMsgFiles().stream()
                .filter(msgFilePath -> !allPaths.contains(msgFilePath))
                .collect(Collectors.toList());
        snapshotWriteContract.removeMsgFilePaths(obsoleteFilePaths);

        return new ExportResult(newMsgFilesPaths, sheets);
    }

    private TranslationSheet processMsgFile(String msgFilePath, List<String> translateTo) {
        List<List<String>> sheetRows = computeTranslationSheetRows(msgFilePath, translateTo);
        Integer sheetId = snapshotWriteContract.registerMsgFile(msgFilePath);
        TranslationSheet translationSheet = newTranslationSheet(sheetRows, sheetId, msgFilePath, translateTo);
        log.info("Gathered '" + translationSheet.getDataRows().size() + "' translation rows from message file '$msgFilePath'.");
        return translationSheet;
    }

    private List<List<String>> computeTranslationSheetRows(String msgFilePath, List<String> translateTo) {
        Map<String, String> primaryMsgs = messageLoader.loadPrimaryMessages(msgFilePath);
        Map<String, Map<String, String>> translations = messageLoader.loadTranslations(msgFilePath, translateTo);

        return messageFileProcessor.prepareTranslationSheet(msgFilePath, primaryMsgs, translations, translateTo);
    }

    private TranslationSheet newTranslationSheet(List<List<String>> sheetRows, Integer sheetId, String msgFilePath, List<String> translateTo) {
        String sheetName = new SheetUtils().getSheetName(msgFilePath, sheetId);
        List<String> header = createSheetHeader(translateTo);

        List<List<String>> allRows = new ArrayList<>();
        allRows.addAll(Collections.singletonList(header));
        allRows.addAll(sheetRows);
        return new TranslationSheet(sheetName, allRows);
    }

    private List<String> createSheetHeader(List<String> targetLangs) {
        List<String> result = new ArrayList<>();
        result.addAll(Arrays.asList("key", "primary"));
        result.addAll(targetLangs);
        return result;
    }

}

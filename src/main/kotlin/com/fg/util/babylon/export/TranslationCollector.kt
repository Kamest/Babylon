package com.fg.util.babylon.export

import com.fg.util.babylon.sheet.SheetUtils
import com.fg.util.babylon.snapshot.TranslationSnapshotReadContract
import com.fg.util.babylon.snapshot.TranslationSnapshotWriteContract

/**
 * Collects messages from primary language message files and from translation message files.
 */
class TranslationCollector(private val messageLoader: MessageLoader,
                           private val messageFileProcessor: MessageFileProcessor,
                           private val snapshotReadContract: TranslationSnapshotReadContract,
                           private val snapshotWriteContract: TranslationSnapshotWriteContract) {

    companion object {
        val log = org.apache.commons.logging.LogFactory.getLog(TranslationCollector::class.java)
    }

    /**
     * Collects message files and given a list of languages to translate to, generates a translation sheet
     * that contains messages in primary language and possibly existing translated messages, if the translated message
     * exists for given language and unless invalidated by the change to its primary message.
     *
     * @param allPaths paths to message files
     * @param translateTo list of languages to translate to
     */
    fun walkPathsAndCollectTranslationSheets(allPaths: Collection<String>,
                                             translateTo: List<Language>): ExportResult {
        val newMsgFilesPaths = allPaths.filter { msgFilePath ->
            !snapshotReadContract.includesMsgFile(msgFilePath)
        }

        val sheets = allPaths.map { msgFilePath ->
            processMsgFile(msgFilePath, translateTo)
        }

        //FIXME: move up
        val obsoleteFilePaths = snapshotReadContract.listMsgFiles() - allPaths
        snapshotWriteContract.removeMsgFilePaths(obsoleteFilePaths)

        return ExportResult(newMsgFilesPaths, sheets)
    }

    private fun processMsgFile(msgFilePath: String, translateTo: List<Language>): TranslationSheet {
        val sheetRows = computeTranslationSheetRows(msgFilePath, translateTo)
        val sheetId = snapshotWriteContract.registerMsgFile(msgFilePath)
        val translationSheet = newTranslationSheet(sheetRows, sheetId, msgFilePath, translateTo)
        log.info("Gathered ${translationSheet.dataRows.size} translation rows from message file '$msgFilePath'.")
        return translationSheet
    }

    private fun computeTranslationSheetRows(msgFilePath: String, translateTo: List<Language>): SheetRows {
        val primaryMsgs = messageLoader.loadPrimaryMessages(msgFilePath)
        val translations = messageLoader.loadTranslations(msgFilePath, translateTo)

        return messageFileProcessor.prepareTranslationSheet(msgFilePath, primaryMsgs, translations, translateTo)
    }

    private fun newTranslationSheet(sheetRows: SheetRows, sheetId: Int, msgFilePath: String, translateTo: List<Language>): TranslationSheet {
        val header = createSheetHeader(msgFilePath, translateTo)
        val sheetName = SheetUtils().getSheetName(msgFilePath, sheetId)
        val allRows = listOf(header) + sheetRows
        return TranslationSheet(sheetName, allRows)
    }

    private fun createSheetHeader(msgFilePath: String, targetLangs: List<Language>): SheetRow =
            listOf("key", "primary") + targetLangs

    data class ExportResult(val pathsOfNewMsgFiles: Iterable<String>,
                            val sheets: List<TranslationSheet>)

    data class TranslationSheet(val sheetName: String,
                                val rows: SheetRows) {

        val dataRows: SheetRows
            get() = rows.subList(1, rows.size)

        val header: SheetRows
            get() = rows.subList(0, 1)

    }

}

typealias Messages = Map<MessageKey, Message>

typealias MsgFilePath = String

typealias Message = String?

typealias MessageKey = String

typealias Language = String

typealias SheetRows = List<SheetRow>

typealias SheetRow = List<String?>
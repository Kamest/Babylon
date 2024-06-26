package one.edee.babylon.export

import one.edee.babylon.sheets.SheetConstants
import one.edee.babylon.export.stats.MessageFileExportStats
import one.edee.babylon.snapshot.TranslationSnapshotReadContract
import java.util.*


typealias Messages = Map<MessageKey, Message>

typealias MsgFilePath = String

typealias Message = String?

typealias MessageKey = String

typealias Language = String

typealias SheetRow = List<String?>

data class SheetContent(val header: List<String>, val dataRows: List<SheetRow>) {
    val dataRowCount = dataRows.size
}


/**
 * Collects translation sheet content for a single message file.
 *
 * @param snapshotReadContract snapshot of current translation state
 */
class MessageFileProcessor(private val snapshotReadContract: TranslationSnapshotReadContract) {

    /**
     * For a single message file path, given primary messages of this message file, languages to translate the messages
     * to and for each language, translations of the primary messages, produces a "translation sheet".
     *
     * Each row of the sheet contains the message key and the message in primary language.
     * In case a translated message is present in some translations but missing for other languages,
     * the existing translated messages are contained in the order of [translationLangs]
     * with null values for the languages where translation is missing.
     *
     * @param msgFile path to message file being processed
     * @param primaryMsgs primary language messages
     * @param translations translations of messages for each of [translationLangs], might be empty for some languages
     * @param translationLangs list of languages the messages should be translated to
     *
     * @return content of the translation sheet as a list of rows
     */
    fun prepareTranslationSheet(msgFile: MsgFilePath,
                                primaryMsgs: Messages,
                                translations: Map<Language, Messages>,
                                translationLangs: List<Language>): Pair<SheetContent, MessageFileExportStats> {
        val newMessageKeys = determineNewMessageKeysUsingComparisonWithTranslation(primaryMsgs, translations)
        val existingMessages = primaryMsgs - newMessageKeys
        val keysOfChangedMsgs = determineChangedPrimaryMsgs(msgFile, existingMessages)
        val keysOfMissingTranslations = determineMissingTranslatedMsgs(existingMessages, translations.values)
        // create a mapping of message keys to their order as found in primary message file
        val primaryMsgKeyOrdering = primaryMsgs.keys
                .withIndex()
                .associate { (index, msgKey) -> msgKey to index }
        val translationSheet = createTranslationSheet(primaryMsgs, translations, translationLangs, newMessageKeys, keysOfChangedMsgs, keysOfMissingTranslations, primaryMsgKeyOrdering)
        val stats = createExportStats(msgFile, newMessageKeys, keysOfChangedMsgs, keysOfMissingTranslations, translationSheet.dataRows.size)
        return translationSheet to stats
    }

    // if primary message is not contained in any translation, then it is a new message
    private fun determineNewMessageKeysUsingComparisonWithTranslation(primaryMsgs: Messages,
                                                                      translations: Map<Language, Messages>): Set<MessageKey> {
        val allTranslationKeys = translations.values
                .map { it.keys }
                .fold(emptySet(), Set<MessageKey>::union)
        return primaryMsgs.keys
                .filter { !allTranslationKeys.contains(it) }
                .toSet()
    }

    /**
     * @return set of keys of messaged that changed since last translation
     */
    private fun determineChangedPrimaryMsgs(msgFile: String,
                                            existingPrimaryMsgs: Messages): Set<MessageKey> =
            if (!snapshotReadContract.includesMsgFile(msgFile)) {
                emptySet()
            } else {
                existingPrimaryMsgs.filter { (msgKey, currentMsg) ->
                    if (!snapshotReadContract.containsMessage(msgKey, msgFile))
                        false
                    else
                        !snapshotReadContract.hasSameMessage(msgKey, msgFile, currentMsg)
                }.keys
            }

    /**
     * Given primary language messages and corresponding translations, returns keys of messages that are missing
     * in at least one translation
     */
    private fun determineMissingTranslatedMsgs(primaryMsgs: Messages,
                                               translations: Collection<Messages>): Set<MessageKey> {
        val translationMsgKeys = translations.map { msgs ->
            msgs.keys
        }
        val keysInEveryTranslationBundle = if (translationMsgKeys.isEmpty())
        // reduce doesn't work on empty collection, fold doesn't play well with intersection operation
            emptySet()
        else
            translationMsgKeys.reduce { acc: Set<MessageKey>, messageKeys ->
                acc.intersect(messageKeys)
            }

        return primaryMsgs.keys - keysInEveryTranslationBundle
    }

    /**
     * Create a translation sheet for a single message file.
     *
     * @param msgFile message file for that the translation sheet will be prepared
     */
    private fun createTranslationSheet(primaryMsgs: Messages,
                                       translations: Map<Language, Messages>,
                                       translationLangs: List<Language>,
                                       newMsgKeys: Set<MessageKey>,
                                       changedMsgKeys: Set<MessageKey>,
                                       missingTransKeys: Set<MessageKey>,
                                       msgKeyOrdering: Map<MessageKey, Int>): SheetContent {
        val populatedRows = missingTransKeys.map { msgKey ->
            populateSheetRow(primaryMsgs, translations, translationLangs, msgKey)
        }

        val noUsableTranslations = changedMsgKeys + newMsgKeys
        val blankRows = noUsableTranslations.map { msgKey ->
            val primaryMsg = primaryMsgs[msgKey]
            val emptyCols = translationLangs.map { null }
            createRow(msgKey, primaryMsg, emptyCols)
        }

        val allRows = blankRows + populatedRows
        val sortedRows = allRows.sortedBy { row ->
            val msgKey = row[0]
            msgKeyOrdering[msgKey]
        }
        val header = createSheetHeader(translationLangs)
        return SheetContent(header, sortedRows)
    }

    /**
     * Creates a single row in a translation sheet.
     */
    private fun populateSheetRow(primaryMsgs: Messages,
                                 translations: Map<Language, Messages>,
                                 translationLangs: List<Language>,
                                 messageKey: MessageKey): SheetRow {
        val primaryMsg = primaryMsgs[messageKey]
        val translatedMsgs = translationLangs.map { lang ->
            translations[lang]?.get(messageKey)
         }
        // if the primary message is missing, there is nothing to translate
        return if (primaryMsg != null) createRow(messageKey, primaryMsg, translatedMsgs) else listOf(messageKey)
    }

    private fun createRow(msgKey: MessageKey, primaryMsg: Message, translations: List<Message>): SheetRow =
            listOf(msgKey, primaryMsg) + translations


    private fun createSheetHeader(targetLangs: List<String>): List<String> =
            listOf(SheetConstants.COL_KEY, SheetConstants.COL_PRIMARY) + targetLangs

    private fun createExportStats(msgFilePath: String,
                                  newMsgKeys: Set<MessageKey>,
                                  changedMsgKeys: Set<MessageKey>,
                                  missingTransKeys: Set<MessageKey>,
                                  totalRows: Int) =
            MessageFileExportStats(msgFilePath, newMsgKeys.size, changedMsgKeys.size, missingTransKeys.size, totalRows);

}

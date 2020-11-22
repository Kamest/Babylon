package com.fg.util.babylon.endtoend.export

import com.fg.util.babylon.db.DataFileManager
import com.fg.util.babylon.entity.Arguments
import com.fg.util.babylon.entity.TranslationConfiguration
import com.fg.util.babylon.enums.Action
import com.fg.util.babylon.processor.I18nFileManager
import com.fg.util.babylon.processor.spring.SpringResourceLoader
import com.fg.util.babylon.service.GoogleSheetApi

class ExportTest {

    fun `when translation data file is empty (first time export) then all localized strings are exported for translation`() {
        val gss = GoogleSheetApi()

        val trSettings = Arguments()
        trSettings.action = Action.EXPORT
        trSettings.googleSheetId = "???"
        trSettings.configFileName = "???"

        val trConfig = TranslationConfiguration()
        trConfig.dataFileName = "???"

        val dfm = DataFileManager(trConfig.dataFileName)
        val rl = SpringResourceLoader()
        val i18nFM = I18nFileManager()
//        val ep = ExportProcessor(gss, dfm, i18nFM, rl, trSettings, trConfig)
//        ep.doExport()


    }

}
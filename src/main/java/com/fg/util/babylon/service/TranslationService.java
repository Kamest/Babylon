package com.fg.util.babylon.service;

import com.fg.util.babylon.db.DataFileManager;
import com.fg.util.babylon.entity.Arguments;
import com.fg.util.babylon.entity.TranslationConfiguration;
import com.fg.util.babylon.enums.Action;
import com.fg.util.babylon.gsheet.TranslationSheetService;
import com.fg.util.babylon.processor.AntPathResourceLoader;
import com.fg.util.babylon.processor.ExportProcessor;
import com.fg.util.babylon.processor.I18nFileManager;
import com.fg.util.babylon.processor.ImportProcessor;
import com.fg.util.babylon.processor.spring.SpringResourceLoader;
import lombok.extern.apachecommons.CommonsLog;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Main service for translation.
 * @author Tomas Langer (langer@fg.cz), FG Forrest a.s. (c) 2019
 */
@CommonsLog
public class TranslationService {

    private final ExportProcessor exportProcessor;
    private final ImportProcessor importProcessor;
    private final Action action;
    private final TranslationConfiguration configuration;

    public TranslationService(GoogleSheetApi gsApi, DataFileManager dfm, Arguments arguments, TranslationConfiguration configuration) {
        AntPathResourceLoader springResLoader = new SpringResourceLoader();
        I18nFileManager i18FileManager = new I18nFileManager();
        TranslationSheetService tss = new TranslationSheetService(gsApi, arguments.getGoogleSheetId());
        exportProcessor = new ExportProcessor(tss, dfm, i18FileManager, springResLoader, configuration);
        importProcessor = new ImportProcessor(gsApi, dfm, i18FileManager, arguments.getGoogleSheetId(), configuration);
        this.configuration = configuration;
        this.action = arguments.getAction();
    }

    public void startTranslation() throws IOException, GeneralSecurityException, InterruptedException {
        long stTime = System.currentTimeMillis();
        switch (action) {
            case EXPORT:
                exportProcessor.doExport(configuration.getMutations());
                break;
            case IMPORT:
                importProcessor.doImport();
                break;
        }
        log.info("Translation done in: " + (System.currentTimeMillis() - stTime) + "ms");
    }

}

package com.fg.util.babylon.db;

import com.fg.util.babylon.snapshot.Snapshot;
import com.fg.util.babylon.config.TranslationConfiguration;
import com.fg.util.babylon.util.JsonUtils;
import lombok.extern.apachecommons.CommonsLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages Snapshot instance.
 */
@CommonsLog
public class SnapshotManager {

    /**
     * Name of the DataFile in Json format which serves as working database for translation process.
     */
    private final Path snapshotFile;

    /**
     * Working DataFile object that changing during the export process. Initial state is given from existing json DataFile.
     * If DataFile not exists then new object DataFile is created.
     */
    private Snapshot snapshot; //FIXME nullable annotation

    //FIXME: OMG why is this needed?
    /** Original untouched DataFile loaded from json file on disk while configuration reading phase. */
    private Snapshot originalSnapshotOnDisk;

    public SnapshotManager(Path snapshotFile) throws IOException {
        this.snapshotFile = snapshotFile;
        loadOriginalDataFile();
    }

    /**
     * This method loads the original DataFile and should be called as the first thing.
     */
    private void loadOriginalDataFile() throws IOException {
        originalSnapshotOnDisk = getExistingDataFileFromDisk(snapshotFile);
        if (originalSnapshotOnDisk == null) {
            originalSnapshotOnDisk = new Snapshot();
        }
    }

    /**
     * Gets original {@link Snapshot} object (before modification). God knows why this is needed. FIXME
     */
    @Deprecated
    public Snapshot getOriginalDataFile() {
        return originalSnapshotOnDisk;
    }

    /**
     * Gets existing {@link Snapshot} object (from Json file on disk) or create new {@link Snapshot} object,
     * according to file name specified by  {@link TranslationConfiguration#getSnapshotName()}
     * @return {@link Snapshot}
     * @throws IOException some exception derived from {@link IOException}
     */
    public Snapshot getOrCreateDataFile() throws IOException {
        if (snapshot == null) {
            snapshot = getExistingDataFileFromDisk(snapshotFile);
            if (snapshot == null) {
                snapshot = new Snapshot();
            }
        }
        return snapshot;
    }

    private Snapshot getExistingDataFileFromDisk(Path snapshotFile) throws IOException {
        File file = snapshotFile.toFile();
        if (file.exists() && file.length() != 0) {
            Snapshot df = JsonUtils.jsonObjFromFile(file, Snapshot.class);
            loadDataPropFilesIds(df);
            return df;
        } else {
            return null;
        }
    }

    /**
     * This map {@link Snapshot#getDataPropFilesById()} is excluded from Json serialization, so after deserialization of
     * {@link Snapshot} from file is necessary to load this map from loaded {@link Snapshot#getDataPropFiles()}.
     * @param df DataFile object with map to load in.
     */
    private void loadDataPropFilesIds(Snapshot df) {
        df.getDataPropFiles().forEach((key, value) -> {
            if (value.getId() != null) {
                df.putDataPropFileById(value.getId(), value);
            } else {
                log.warn("Id for path \"" + key + "\" not found.");
            }
        });
    }

}

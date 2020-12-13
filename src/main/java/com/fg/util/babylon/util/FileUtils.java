package com.fg.util.babylon.util;

import java.io.File;
import java.io.IOException;

public class FileUtils {

    /**
     * Opens file if exists, throws exception otherwise.
     *
     * @param path path to file
     * @return file
     * @throws IllegalArgumentException if the file does not exist
     */
    public static File fileFromPathOrThrow(String path) {
        File propFile = new File(path);
        if (!propFile.exists()) {
            throw new IllegalArgumentException("Message file '" + path + "' does not exist.");
        }
        return propFile;
    }

    /**
     * Opens file if exists, or creates it if not exist yet.
     *
     * @param path path to file
     * @return existing or new file
     * @throws IOException if the file does not exist and could not be created
     */
    public static File fileFromPathOrCreate(String path) throws IOException {
        File propFile = new File(path);
        if (!propFile.exists()) {
            propFile.createNewFile();
        }
        return propFile;
    }

}

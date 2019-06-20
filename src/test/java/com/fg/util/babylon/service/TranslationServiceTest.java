package com.fg.util.babylon.service;

import com.fg.util.babylon.entity.Configuration;
import com.fg.util.babylon.util.JsonUtils;
import com.fg.util.babylon.util.TestUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.junit.Test;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Tomas Langer (langer@fg.cz), FG Forrest a.s. (c) 2019
 */
@CommonsLog
public class TranslationServiceTest {

    /**
     * Checks of {@link Configuration} serialization from object to string and back.
     * @throws IOException some exception derived from {@link IOException}
    */
    @Test
    public void checkConfigSerializationAndDeserialization() throws IOException {
        Configuration configuration = TestUtils.createTestConfiguration();
        String jsonString = JsonUtils.objToJsonString(configuration, true);
        assertFalse("Json String is null or empty", StringUtils.isEmpty(jsonString));
        log.info(jsonString);
        Configuration configFromStr = JsonUtils.jsonObjFromString(jsonString, Configuration.class);
        assertNotNull("Json object from string is null", configFromStr);
    }

    /**
     * Checks creation of json configuration file.
     * @throws IOException some exception derived from {@link IOException}
    */
    @Test
    public void checkCreateConfigurationFile() throws IOException {
        File file = new File("test-config.json");
        Configuration configuration = TestUtils.createTestConfiguration();
        JsonUtils.objToJsonFile(file, configuration, true);
        assertFalse("primary data file is empty", configuration.getDataFileName().isEmpty());
        assertFalse("path is empty", configuration.getPath().isEmpty());
        assertFalse("mutations is empty", configuration.getMutations().isEmpty());
        assertTrue("Configuration file not generated", file.exists());
    }

}
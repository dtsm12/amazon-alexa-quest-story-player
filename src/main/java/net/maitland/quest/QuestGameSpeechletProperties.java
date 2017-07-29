package net.maitland.quest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by David on 29/07/2017.
 */
public class QuestGameSpeechletProperties {

    public static final String SUPPORT_APPLICATION_PREFIX = "applicationId";
    public static final String QUEST_FILE_PROPERTY = "questFile";

    public static final String FILE_NAME_PROPERTY = "questPropertiesFile";
    public static final String PROPERTIES_FILE_NAME = "quest.properties";

    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechletProperties.class);

    public static Set<String> getSupportedApplicationIds() {
        Set<String> supportedApplicationIds = null;
        try {
            Properties properties = getQuestGameSpeechletProperties();
            supportedApplicationIds = new HashSet<>();

            int index = 0;
            String attributeId = properties.getProperty(SUPPORT_APPLICATION_PREFIX + index++);

            while (attributeId != null) {
                supportedApplicationIds.add(attributeId);
                attributeId = properties.getProperty(SUPPORT_APPLICATION_PREFIX + index++);
            }

            if (supportedApplicationIds.size() < 1) {
                throw new Exception("No supported " + SUPPORT_APPLICATION_PREFIX + "{X} properties found");
            }
        } catch (Exception e) {
            log.error("Error getting supported application ids: " + e.getMessage());
        }

        return supportedApplicationIds;
    }

    public static String getQuestFileName() throws Exception {
        String questFileName = getQuestGameSpeechletProperties().getProperty(QUEST_FILE_PROPERTY);
        if (questFileName == null) {
            throw new Exception("No " + QUEST_FILE_PROPERTY + " property found");
        }
        return questFileName;
    }

    protected static Properties getQuestGameSpeechletProperties() throws Exception {

        String propertyFileName = System.getProperty(FILE_NAME_PROPERTY, PROPERTIES_FILE_NAME);

        ClassLoader classLoader = QuestGameSpeechletProperties.class.getClassLoader();
        File file = new File(classLoader.getResource(propertyFileName).getFile());
        Properties properties = new Properties();

        try (InputStream is = new FileInputStream(file)) {

            if (is != null) {
                properties.load(is);
            } else {
                throw new FileNotFoundException(propertyFileName + " file not found");
            }
        }

        return properties;
    }

}

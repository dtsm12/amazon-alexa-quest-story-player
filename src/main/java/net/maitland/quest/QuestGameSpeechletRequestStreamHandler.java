
/**
 * Created by David on 04/12/2016.
 */
package net.maitland.quest;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is created by the Lambda environment when a request comes in. All calls will be
 * dispatched to the Speechlet passed into the super constructor.
 */
public final class QuestGameSpeechletRequestStreamHandler extends
        SpeechletRequestStreamHandler {
    private static Set<String> supportedApplicationIds = null;
    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechletRequestStreamHandler.class);

    static {

        log.info("Loading supported application ids");
        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        try {
            supportedApplicationIds = QuestGameSpeechletProperties.getSupportedApplicationIds();
        }
        catch(Exception e)
        {
            log.error("Error getting supported application ids: " + e.getMessage());
        }

        log.info("Loaded supported application ids: " + supportedApplicationIds);
    }

    public QuestGameSpeechletRequestStreamHandler() {
        super(new QuestGameSpeechlet(), supportedApplicationIds);
    }
}

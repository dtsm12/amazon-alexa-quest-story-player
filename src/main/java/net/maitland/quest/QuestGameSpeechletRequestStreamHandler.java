
/**
 * Created by David on 04/12/2016.
 */
package net.maitland.quest;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is created by the Lambda environment when a request comes in. All calls will be
 * dispatched to the Speechlet passed into the super constructor.
 */
public final class QuestGameSpeechletRequestStreamHandler extends
        SpeechletRequestStreamHandler {
    private static final Set<String> supportedApplicationIds;

    static {
        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        supportedApplicationIds = new HashSet<>();
        supportedApplicationIds.add("amzn1.ask.skill.22191f24-1113-41c7-8419-a6787a54da7b");
    }

    public QuestGameSpeechletRequestStreamHandler() {
        super(new QuestGameSpeechlet(), supportedApplicationIds);
    }
}

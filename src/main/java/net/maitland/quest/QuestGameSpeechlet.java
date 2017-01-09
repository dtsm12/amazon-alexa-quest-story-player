/**
 * Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at
 * <p>
 * http://aws.amazon.com/apache2.0/
 * <p>
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package net.maitland.quest;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import net.maitland.quest.model.Choice;
import net.maitland.quest.model.Quest;
import net.maitland.quest.model.QuestStateStation;
import net.maitland.quest.player.ChoiceNotPossibleException;
import net.maitland.quest.player.ConsolePlayer;
import net.maitland.quest.player.QuestInstance;
import net.maitland.quest.player.QuestStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by David on 04/12/2016.
 */
public class QuestGameSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechlet.class);

    private static final String QUEST_INSTANCE = "QUEST_INSTANCE";

    /**
     * The key to get the item from the intent.
     */
    private static final String ITEM_SLOT = "Item";

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // Here we are prompting the user for input
        return speakNextPassage(session);
    }

    public SpeechletResponse speakNextPassage(Session session) {
        return speakNextPassage(session, null);
    }

    public SpeechletResponse speakNextPassage(Session session, String choiceNumber) {

        // response text
        StringBuilder response = new StringBuilder();

        try {

            // convert choice number
            int choice = Integer.parseInt(choiceNumber);

            // get QuestInstance
            QuestInstance questInstance = (QuestInstance) session.getAttribute(QUEST_INSTANCE);
            if (questInstance == null) {
                questInstance = getQuestInstance();
                session.setAttribute(QUEST_INSTANCE, questInstance);
            }
            QuestStateStation station = questInstance.getNextStation(choice);
            response.append(getStationPssage(station));
        } catch (Exception e) {
            response.append("Encountered the following error.");
            response.append(e.getMessage());
            response.append(". Restarting story.");
            clearQuestInstance(session);
        }

        if (response.length() == 0) {
            response.append("Story has ended.");
            clearQuestInstance(session);
        }

        return newAskResponse(response.toString(), response.toString());
    }

    protected void clearQuestInstance(Session session) {
        session.setAttribute(QUEST_INSTANCE, null);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        if ("ChoiceIntent".equals(intentName)) {
            return speakNextPassage(session, intent.getSlot("Choice").getValue());
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelp();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        clearQuestInstance(session);
    }

    /**
     * Creates a {@code SpeechletResponse} for the HelpIntent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelp() {
        String speechOutput =
                "This is a story. Good eh?";
        return newAskResponse(speechOutput, speechOutput);
    }

    /**
     * Wrapper for creating the Ask response. The OutputSpeech and {@link Reprompt} objects are
     * created from the input strings.
     *
     * @param stringOutput the output to be spoken
     * @param repromptText the reprompt for if the user doesn't reply or is misunderstood.
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, String repromptText) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText(stringOutput);

        PlainTextOutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech();
        repromptOutputSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);

        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

    protected String getStationPssage(QuestStateStation questStation) {
        List<Choice> choices;
        StringBuilder passage = new StringBuilder();

        choices = questStation.getChoices();

        if (choices.size() != 0) {

            passage.append(questStation.getText());

            passage.append("These are your choices:");

            for (Choice c : choices) {
                passage.append(String.format("\t%s: %s", c.getStation().getId(), c.getText()));
            }

            passage.append("Enter your choice:");
        }

        return passage.toString();
    }

    protected QuestInstance getQuestInstance() throws Exception {
        return new QuestInstance(getQuest());
    }

    protected Quest getQuest() throws Exception {
        Quest q = null;
        InputStream is = null;
        try {
            is = ConsolePlayer.class.getClassLoader().getResourceAsStream("bargames-quest.xml");
            SaxQuestParser qp = new SaxQuestParser();
            q = qp.parseQuest(is);

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return q;
    }
}

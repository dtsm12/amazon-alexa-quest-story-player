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
import net.maitland.quest.model.GameInstance;
import net.maitland.quest.model.Quest;
import net.maitland.quest.model.QuestState;
import net.maitland.quest.player.ConsolePlayer;
import net.maitland.quest.player.QuestStateChoice;
import net.maitland.quest.player.QuestStateException;
import net.maitland.quest.player.QuestStateStation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Created by David on 04/12/2016.
 */
public class QuestGameSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechlet.class);

    private static final String GAME_INSTANCE = "GAME_INSTANCE";

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
        return speakNextPassage(session, "0");
    }

    public SpeechletResponse speakNextPassage(Session session, String choiceNumber) {

        // response text
        StringBuilder response = new StringBuilder();

        boolean hasEnded = false;

        try {

            // convert choice number
            int choice = Integer.parseInt(choiceNumber);

            // Get quest
            Quest quest = getQuest();

            // get GameInstance
            GameInstance gameInstance = getGameInstance(session, quest);
            QuestStateStation station = null;

            if(choice == 0)
            {
                response.append(quest.getAbout().getTitle());
            }

            // keep adding text until zero or more than 1 choice
            while(station == null || station.getChoices().size() == 1) {
                choice = station == null ? choice : 1;
                station = quest.getNextStation(gameInstance, choice);
                response.append(getStationPassage(station));
            }

            if (response.length() == 0 || station.getChoices().size() == 0) {
                hasEnded = true;
                clearQuestInstance(session);
            }

        } catch (Exception e) {
            response.append("Encountered the following error.");
            response.append(e.getMessage());
            hasEnded = true;
            clearQuestInstance(session);
        }

        return newAskResponse(response.toString(), response.toString(), hasEnded);
    }

    protected GameInstance getGameInstance(Session session, Quest quest) throws QuestStateException {
        GameInstance gameInstance = quest.newGameInstance();

        log.debug("Getting {} attribute from session", GAME_INSTANCE);

        Object gameData = session.getAttribute(GAME_INSTANCE);

        if (gameData != null) {
            log.debug("Found {} attribute in session: {}", GAME_INSTANCE, gameData);
            Map gameDataMap = (Map) gameData;
            Object questPath = gameDataMap.get("questPath");
            log.debug("Found questPath in session as {}", questPath.getClass().getName());
            Object currentState = gameDataMap.get("currentState");
            log.debug("Found currentState in session as {}", currentState.getClass().getName());
            Object previousState = gameDataMap.get("previousState");
            log.debug("Found previousState in session as {}", previousState.getClass().getName());

            gameInstance.setQuestPath(new ArrayDeque<String>((List<String>) questPath));
            gameInstance.setQuestState(new QuestState(((Map<String, Map<String, String>>) currentState).get("attributes")));
            gameInstance.setPreviousQuestState(new QuestState(((Map<String, Map<String, String>>) previousState).get("attributes")));
        }

        session.setAttribute(GAME_INSTANCE, gameInstance);

        return gameInstance;
    }

    protected void clearQuestInstance(Session session) {
        session.setAttribute(GAME_INSTANCE, null);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        log.debug("intentName={}", intentName);
        for (String slotName : intent.getSlots().keySet()) {
            log.debug("slotName={}, slotValue={}", slotName, intent.getSlot(slotName).getValue());
        }

        if ("Choice".equals(intentName)) {
            return speakNextPassage(session, intent.getSlot("Choice").getValue());
        } else if ("Choose".equals(intentName)) {
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
        return newAskResponse(speechOutput, speechOutput, true);
    }

    /**
     * Wrapper for creating the Ask response. The OutputSpeech and {@link Reprompt} objects are
     * created from the input strings.
     *
     * @param stringOutput the output to be spoken
     * @param repromptText the reprompt for if the user doesn't reply or is misunderstood.
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, String repromptText, boolean noResponse) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText(stringOutput);

        PlainTextOutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech();
        repromptOutputSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);

        SpeechletResponse response = null;

        if(noResponse) {
            SpeechletResponse.newTellResponse(outputSpeech);
        }
        else {
            SpeechletResponse.newAskResponse(outputSpeech, reprompt);
        }

        return response;
    }

    protected String getStationPassage(QuestStateStation questStation) {
        List<QuestStateChoice> choices;
        StringBuilder passage = new StringBuilder();

        passage.append(questStation.getText());

        choices = questStation.getChoices();

        if (choices.size() == 1) {

            passage.append(choices.get(0).getText());

        } else if (choices.size() != 0) {

            passage.append("These are your choices. ");

            for (int i = 0; i < choices.size(); i++) {
                QuestStateChoice c = choices.get(i);
                passage.append(String.format("Option %s: %s. ", i + 1, c.getText()));
            }

            passage.append(" Make your choice. ");
        }

        return passage.toString();
    }

    protected Quest getQuest() throws Exception {
        Quest q = null;
        InputStream is = null;
        try {
            is = ConsolePlayer.class.getClassLoader().getResourceAsStream("chance-quest.xml");
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

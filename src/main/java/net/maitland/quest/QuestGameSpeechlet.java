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
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import net.maitland.quest.model.*;
import net.maitland.quest.model.Game;
import net.maitland.quest.parser.sax.SaxQuestParser;
import net.maitland.quest.player.ConsolePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * Created by David on 04/12/2016.
 */
public class QuestGameSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechlet.class);

    private static final String GAME_INSTANCE = "GAME_INSTANCE";

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
        } else if ("AMAZON.YesIntent".equals(intentName)) {
            return restartQuest(session);
        } else if ("AMAZON.NoIntent".equals(intentName)) {
            clearQuestInstance(session);
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");
            return SpeechletResponse.newTellResponse(outputSpeech);
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

    protected SpeechletResponse restartQuest(final Session session) {
        clearQuestInstance(session);
        return speakNextPassage(session, "0", false);
    }

    public SpeechletResponse speakNextPassage(Session session) {
        return speakNextPassage(session, "0");
    }

    public SpeechletResponse speakNextPassage(Session session, String choiceNumber) {
        return speakNextPassage(session, choiceNumber, true);
    }

    public SpeechletResponse speakNextPassage(Session session, String choiceNumber, boolean includeIntroAtStart) {

        // response text
        StringBuilder response = new StringBuilder();

        boolean hasEnded = false;

        try {

            // Get quest
            Quest quest = getQuest();

            // get Game
            Game game = getGame(session, quest);
            GameStation station = null;

            try {

                // convert choice number
                int choice = Integer.parseInt(choiceNumber);

                // add quest info if just starting
                if (choice == 0 && includeIntroAtStart) {
                    response.append(quest.getAbout().getTitle());
                    response.append(" by ");
                    response.append(quest.getAbout().getAuthor());
                    response.append('\n');
                    response.append(quest.getAbout().getIntro());
                    response.append('\n');
                }

                // keep adding text until zero or more than 1 choice
                while (station == null || station.getChoices().size() == 1) {
                    choice = station == null ? choice : 1;
                    game.setChoiceIndex(choice);
                    station = quest.getNextStation(game);
                    response.append(getStationPassage(station));
                }

                // quest has ended
                if (response.length() == 0 || station.getChoices().size() == 0) {
                    clearQuestInstance(session);
                    response.append("Do you want to play again ?");
                }

            } catch (Exception e) {

                log.error("Error getting next passage.", e);
                response.append("I had a problem processing your choice.");
                station = quest.getCurrentStation(game);
                response.append(getStationPassage(station));
            }

        } catch (Exception e) {
            log.error("Error getting next passage.", e);
            response.append("Encountered the following error.");
            response.append(e.getMessage());
            hasEnded = true;
            clearQuestInstance(session);
        }

        return newAskResponse(response.toString(), response.toString(), hasEnded);
    }

    protected Game getGame(Session session, Quest quest) throws Exception {

        Game game = null;

        log.debug("Getting {} attribute from session", GAME_INSTANCE);

        Object gameData = session.getAttribute(GAME_INSTANCE);

        if (gameData == null) {
            game = quest.newGameInstance();
        } else {
            log.debug("Found {} attribute in session: {}", GAME_INSTANCE, gameData);
            Map gameDataMap = (Map) gameData;
            game = Game.fromCollectionStructure(gameDataMap);
        }

        session.setAttribute(GAME_INSTANCE, game);

        return game;
    }

    protected void clearQuestInstance(Session session) {
        session.setAttribute(GAME_INSTANCE, null);
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

        if (noResponse) {
            response = SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            response = SpeechletResponse.newAskResponse(outputSpeech, reprompt);
        }

        return response;
    }

    protected String getStationPassage(GameStation questStation) {
        List<GameChoice> choices;
        StringBuilder passage = new StringBuilder();

        passage.append(questStation.getText());

        choices = questStation.getChoices();

        if (choices.size() == 1) {

            passage.append(choices.get(0).getText());

        } else if (choices.size() != 0) {

            passage.append("These are your choices. ");

            for (int i = 0; i < choices.size(); i++) {
                GameChoice c = choices.get(i);
                passage.append(String.format("Option %s: %s", i + 1, c.getText()));
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

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        clearQuestInstance(session);
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // Here we are prompting the user for input
        return speakNextPassage(session);
    }
}

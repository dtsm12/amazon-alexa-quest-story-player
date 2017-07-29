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
import net.maitland.quest.player.ChoiceNotPossibleException;
import net.maitland.quest.player.ConsolePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Created by David on 04/12/2016.
 */
public class QuestGameSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuestGameSpeechlet.class);

    private static final String SESSION_MODE = "SESSION_MODE";
    private static final String SESSION_MODE_GAME = "GAME";
    private static final String SESSION_MODE_HELP = "HELP";
    private static final String SESSION_MODE_RESTART = "RESTART";
    private static final String GAME_INSTANCE = "GAME_INSTANCE";

    private static final int START_STATION = 0;
    private static final int CURRENT_STATION = -1;

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return speakFirstPassage(session);
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

        SpeechletResponse response = null;
        String sessionMode = (String) session.getAttribute(SESSION_MODE);

        switch (intentName) {
            case "Choose":
            case "ChoiceIntent":
                response = speakNextPassage(session, intent.getSlot("Choice").getValue());
                break;

            case "AMAZON.StopIntent":
            case "AMAZON.CancelIntent":
                response = endSession();
                break;

            case "AMAZON.HelpIntent":
                response = provideHelp(session);
                break;

            case "AMAZON.YesIntent":
            case "AMAZON.NoIntent":
                if (SESSION_MODE_RESTART.equals(sessionMode)) {
                    response = "AMAZON.YesIntent".equals(intentName) ? restartQuest(session) : endSession();
                }
                else if (SESSION_MODE_HELP.equals(sessionMode)) {
                    response = speakCurrentPassage(session, "AMAZON.YesIntent".equals(intentName));
                }
                else
                {
                    response = speakCurrentPassage(session, false);
                }
                break;

            default:
                response = speakCurrentPassage(session, false);
                break;
        }

        return response;
    }

    protected SpeechletResponse endSession() {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("Goodbye");
        return SpeechletResponse.newTellResponse(outputSpeech);
    }

    protected SpeechletResponse provideHelp(Session session) {
        // set mode to help
        session.setAttribute(SESSION_MODE, SESSION_MODE_HELP);

        StringBuilder response = new StringBuilder();
        response.append("You must choose one of the options by saying \"Option\" and then the number of your choice. ");
        response.append("For example you might say \"Option 1\". ");
        response.append("Do you want to hear the last section again ?");
        return newAskResponse(response.toString(), response.toString(), false);
    }

    public SpeechletResponse speakFirstPassage(Session session) {
        return speakNextPassage(session, START_STATION, true, true);
    }

    public SpeechletResponse speakCurrentPassage(Session session, boolean speakText) {
        return speakNextPassage(session, CURRENT_STATION, false, speakText);
    }

    public SpeechletResponse speakNextPassage(Session session, String choiceNumber) {

        SpeechletResponse response = null;

        try {
            // convert choice number
            int choice = Integer.parseInt(choiceNumber);
            response = speakNextPassage(session, choice, true, true);
        } catch (NumberFormatException e) {
            response = speakCurrentPassage(session, false);
        }

        return response;
    }

    protected SpeechletResponse restartQuest(final Session session) {
        clearQuestInstance(session);
        return speakNextPassage(session, START_STATION, false, true);
    }

    public SpeechletResponse speakNextPassage(Session session, int choice, boolean includeIntroAtStart, boolean speakText) {

        // set mode to game
        session.setAttribute(SESSION_MODE, SESSION_MODE_GAME);

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

                // add quest info if just starting
                if (choice == 0 && includeIntroAtStart) {
                    response.append(quest.getAbout().getTitle());
                    response.append('\n');
                    response.append(quest.getAbout().getIntro());
                    response.append('\n');
                }

                // get next or current station
                if (choice == -1) {
                    station = quest.getCurrentStation(game);
                } else {
                    game.setChoiceIndex(choice);
                    station = quest.getNextStation(game);
                }
                response.append(getStationPassage(station, speakText));

                // keep adding text until zero or more than 1 choice
                while (station.getChoices().size() == 1) {
                    game.setChoiceIndex(1);
                    station = quest.getNextStation(game);
                    response.append(getStationPassage(station, speakText));
                }

                // quest has ended
                if (response.length() == 0 || station.getChoices().size() == 0) {
                    clearQuestInstance(session);

                    // set mode to restart
                    session.setAttribute(SESSION_MODE, SESSION_MODE_RESTART);

                    response.append("Do you want to play again ?");
                }

            } catch (Exception e) {

                log.error("Error getting next passage.", e);

                if(e instanceof ChoiceNotPossibleException) {
                    response.append(e.getMessage());
                }
                else {
                    response.append("I had a problem processing your choice.");
                }
                response.append('\n');
                station = quest.getCurrentStation(game);
                response.append(getStationPassage(station, true));
            }

        } catch (Exception e) {
            log.error("Error getting quest or game state.", e);
            response.append("Encountered the following error.");
            response.append('\n');
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

    protected String getStationPassage(GameStation questStation, boolean speakText) {
        List<GameChoice> choices;
        StringBuilder passage = new StringBuilder();

        if (speakText) {
            passage.append(questStation.getText());
        }

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
            String questFileName = QuestGameSpeechletProperties.getQuestFileName();
            is = this.getClass().getClassLoader().getResourceAsStream(questFileName);
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
}

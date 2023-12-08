package my_custom_component;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.ModuleExtension;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.RemovePiece;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.IntConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.tools.DataArchive;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
        MIT License

        Copyright (c) 2023 MARCELLO BARROZO NETTO

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.

 */
public final class AnimatedDice extends ModuleExtension implements CommandEncoder, Buildable{
    private GameModule gameModule;
    private final DataArchive dataArchive;
    private static final String COMMAND_PREFIX = "ANIMATED_3D_DICE:";
    private final String ICONS_IMAGES_PATH = "Icons/";
    private final String RED_DIE_FOLDER_PATH = "DiceImages/RED DIE/";
    private final String HESITANT_RED_DIE_FOLDER_PATH = "DiceImages/HRED DIE/";
    private final String WHITE_DIE_FOLDER_PATH = "DiceImages/WHITE DIE/";
    private final String HESITANT_WHITE_DIE_FOLDER_PATH = "DiceImages/HWHITE DIE/";
    private final String RED_HESITANT_DIE_INDEXES = "DiceImages/Hesitant Red Die Indexes.txt";
    private final String WHITE_HESITANT_DIE_INDEXES = "DiceImages/Hesitant White Die Indexes.txt";
    private final String ANIMATED_DICE_PREFERENCES = "Animated 3D Dice";
    private final String FRAME_RATE_SETTINGS = "frameRateSettings";
    private final String DICE_POSITION_SETTINGS = "dicePositionSettings";
    private final String ONE_DIE_BUTTON_SETTINGS = "oneDieButtonSettings";
    private final String TWO_DICE_BUTTON_SETTINGS = "twoDiceButtonSettings";
    private final String BUTTONS_INDEX_SETTINGS = "buttonsIndexSettings";
    private final String DICE_ON_SCREEN_DURATION_SETTINGS = "diceOnScreenDurationSettings";
    private final String SHUFFLE_SOUND_SETTINGS = "shuggleSoundSettings";
    private final String DICE_SOUND_SETTINGS = "diceSoundSettings";
    private final String DISPLAY_NUMBERS_SETTINGS = "displayNumbersSetting";
    private final String DISPLAY_DICE_IMAGES_SETTINGS = "displayDiceImagesSettings";
    private final String TOOLTIP_TEXT = "<html><ul><li>Left Click to <b>ROLL</b>. " +
            "<li>Right Click to get a <b>QUICK ROLL</b>, without animation." +
            "<li>Keep button pressed to <b>SHUFFLE</b>. Mouse movement will be registered until the button is released and will affect the final result." +
            "<br><br>Go to <b>PREFERENCES</b> to change Animated Dice properties like speed, position etc.</li></html>";
    private int dicePositionSettings;
    private int MAX_HORIZONTAL_OFFSET = 0;
    private final int IMAGE_SIZE = 250;
    private volatile boolean isImageVisible; // establish if last dice frame is still visible in the screen so that the button is in Hide mode.
    private volatile boolean isAnimationInProgress = false; // prevents mousePress action before animation finishes.
    private JButton oneDieButton;
    private boolean oneDieButtonVisible;
    private JButton twoDiceButton;
    private boolean twoDiceButtonVisible;
    private int diceToolbarIndex;
    private int[] mouseBiasFactor; // Set of ints generated by mouse movement which will influence dice results
    private final int NUMBER_OF_DICE = 2; // number of dices rolled
    private final int NUMBER_OF_SIDES = 6; // number of sides of a die
    private long imageDelay; // The number of MILLISECONDS between the display actions.
    private int animationSpeed;
    private boolean isShuffleSoundOn;
    private boolean isDiceSoundOn;
    private boolean displayNumbers;
    private boolean displayDiceImages;
    private final int MAX_ANIMATION_SPEED = 50;
    private final int MIN_ANIMATION_SPEED = 1;
    private final int MIN_VIABLE_SPEED = 35;
    private int onScreenDuration;
    private final int MAX_ON_SCREEN_DURATION = 1000;
    private final int MIN_ON_SCREEN_DURATION = 0;
    private ScheduledExecutorService scheduler; // Controls the frame rate of the displayed images
    private final HashMap<String, HashMap<Integer, ArrayList<Image>>> imagesCache1; // set of preloaded images for each die and each result on the form: "Red": 6: List of images
    private final HashMap<String, HashMap<Integer, ArrayList<Image>>> imagesCache2;
    private volatile ProjectingPiece redProjectingPiece = null;
    private volatile ProjectingPiece whiteProjectingPiece = null;
    private volatile boolean isFeedingCache1;
    private volatile boolean isFeedingCache2;
    private boolean oddRound = true;
    private int numberOfDice;
    private final HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> redHesitantDieFolderBuilder; //Keep data taken from a txt file to reproduce the folder names for hesitant dice. Key: Animation number / Key: die result / value: rejected die value (middle number in folder name)
    private final HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> whiteHesitantDieFolderBuilder;
    private final HashMap<String, Object[]> lastAnimationUsed; // Keeps track of the last animation used to prevent immediate repetition and to control hesitant dice probabilities. Must have the form ["Red", [4, true]] where true is for hesitant die
    private final double hesitantDieProbability;
    private final int NUMBER_OF_DIE_ANIMATIONS = 27; // ALTER THAT IF MORE ANIMATIONS ARE ADEED
    private final int NUMBER_OF_HESITANT_DIE_ANIMATIONS = 5;
    private final Object soundsLock = new Object();
    private final Object removePieceSyncLock = new Object();
    private Clip dieClip;
    private Clip diceClip;
    private Clip shuffleClip;
    private int[] results;
    private boolean thisCaller = false;
    private final Map currentMap;
    private final String playerId = GlobalOptions.getInstance().getPlayerId();


    public AnimatedDice(){
        super(GameModule.getGameModule().getDataArchive());
        isImageVisible = false;
        gameModule = GameModule.getGameModule();
        dataArchive = gameModule.getDataArchive();
        currentMap = GameModule.getGameModule().getComponentsOf(Map.class).get(0);
        dicePositionSettings = 0;
        animationSpeed = 27;
        onScreenDuration = 5;
        oneDieButtonVisible = true;
        twoDiceButtonVisible = true;
        isShuffleSoundOn = true;
        isDiceSoundOn = true;
        displayNumbers = true;
        displayDiceImages = true;
        hesitantDieProbability = 0.3; // must be multiplied by animations Ratio to ge the actual probability
        lastAnimationUsed = new HashMap<>(){{
            put("white", new Object[]{0, false});
            put("red", new Object[]{0, false});
        }};
        // INITIALIZES THE PRELOADED IMAGES HASHMAP
        imagesCache1 = new HashMap<>();
        imagesCache2 = new HashMap<>();
        imagesCache1.put("white", new HashMap<>());
        imagesCache1.put("red", new HashMap<>());
        imagesCache2.put("white", new HashMap<>());
        imagesCache2.put("red", new HashMap<>());
        for (int i = 1; i <= NUMBER_OF_SIDES; i++){
            imagesCache1.get("white").put(i, new ArrayList<>());
            imagesCache1.get("red").put(i, new ArrayList<>());
            imagesCache2.get("white").put(i, new ArrayList<>());
            imagesCache2.get("red").put(i, new ArrayList<>());
        }

        // CREATE THE ARRAY INDICATING WHAT FOLDERS BRING HESITANT DIE ANIMATIONS
        redHesitantDieFolderBuilder = CreateHesitantDieFolderBuilder(RED_HESITANT_DIE_INDEXES);
        whiteHesitantDieFolderBuilder = CreateHesitantDieFolderBuilder(WHITE_HESITANT_DIE_INDEXES);

        // GET RESOURCES
        loadSounds(); // Preloads sounds for dices

        // LOAD FIRST SET OF IMAGES
        DrawDiceFolders(imagesCache1);
        DrawDiceFolders(imagesCache2);
    }

    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof GameModule) {
            gameModule = (GameModule) parent;

            // ADDS A COMMAND ENCODER FOR RunDiceAnimationCommand THAT WILL MAKE THE ANIMATION RUN ON THE OPPONENT'S COMPUTER WHEN THE ROLL BUTTON IS PRESSED
            gameModule.addCommandEncoder(this);
            ((GameModule) parent).getGameState().addGameComponent(this);

            // CREATE BUTTONS
            URL iconURL = null;
            try {
                iconURL = dataArchive.getURL(ICONS_IMAGES_PATH + "RollDieButton.png");
            } catch (IOException e){
                e.printStackTrace();
            }
            ImageIcon icon = null;
            if (iconURL != null)
                icon = new ImageIcon(iconURL);
            oneDieButton = new DelayedActionButton("", icon, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    thisCaller = true; // establish that this player is calling the roll and not the opponent
                    executeRoll(1);
                }
            });
            oneDieButton.setToolTipText(TOOLTIP_TEXT);
            oneDieButton.setMargin(new Insets(0,3,0,3));
            try {
                iconURL = dataArchive.getURL(ICONS_IMAGES_PATH + "RollDiceButton.png");
            } catch (IOException e){
                e.printStackTrace();
            }
            if (iconURL != null)
                icon = new ImageIcon(iconURL);
            twoDiceButton = new DelayedActionButton("", icon, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    thisCaller = true;
                    executeRoll(2);
                }
            });
            twoDiceButton.setToolTipText(TOOLTIP_TEXT);
            twoDiceButton.setMargin(new Insets(0,3,0,3));


            // ADD SETTINGS TO PREFERENCE WINDOW
            JToolBar toolBar = gameModule.getToolBar();

            // ...BUTTONS INDEX
            final IntConfigurer buttonsIndexSettings = new IntConfigurer(BUTTONS_INDEX_SETTINGS, "Buttons position ", diceToolbarIndex);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, buttonsIndexSettings);
            diceToolbarIndex = (int) gameModule.getPrefs().getValue(BUTTONS_INDEX_SETTINGS);

            buttonsIndexSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    int typedValue = (int) gameModule.getPrefs().getValue(BUTTONS_INDEX_SETTINGS);
                    int numberOfSlots = toolBar.getComponentCount() - 1;
                    if (typedValue < 0){
                        gameModule.getPrefs().setValue(BUTTONS_INDEX_SETTINGS, 1);
                        diceToolbarIndex = 1;
                    }
                    if (typedValue > numberOfSlots){
                        gameModule.getPrefs().setValue(BUTTONS_INDEX_SETTINGS, numberOfSlots);
                        diceToolbarIndex = numberOfSlots;
                    } else {
                        diceToolbarIndex = typedValue;
                    }
                    toolBar.add(oneDieButton, diceToolbarIndex);
                    toolBar.add(twoDiceButton, diceToolbarIndex);
                    oneDieButton.setVisible((boolean) gameModule.getPrefs().getValue(ONE_DIE_BUTTON_SETTINGS));
                    twoDiceButton.setVisible((boolean) gameModule.getPrefs().getValue(TWO_DICE_BUTTON_SETTINGS));

                    //gameModule.getToolBar().add(oneDieButton, diceToolbarIndex - 1);
                    toolBar.updateUI();
                }
            });

            // INITIALIZE BUTTONS IN TOOLBAR WITH PROPER INDEX
            if (diceToolbarIndex > toolBar.getComponentCount()) // ----------->>>   SEE IF IT IS POSSIBLE TO ADD BUTTONS AFTER ALL OTHERS ARE ADDED, OR EXTENSIONS INITIALIZED LATER WILL DISLODGE OUR BUTTONS IF SET WITH LAGE INDEX NUMBER
                diceToolbarIndex = toolBar.getComponentCount();
            toolBar.add(oneDieButton, diceToolbarIndex);
            toolBar.add(twoDiceButton, diceToolbarIndex);

            // ...HIDE BUTTONS
            final BooleanConfigurer oneDieButtonSettings = new BooleanConfigurer(ONE_DIE_BUTTON_SETTINGS, "One die button ", oneDieButtonVisible);
            final BooleanConfigurer twoDiceButtonSettings = new BooleanConfigurer(TWO_DICE_BUTTON_SETTINGS, "Two dice button ", twoDiceButtonVisible);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, oneDieButtonSettings);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, twoDiceButtonSettings);
            oneDieButtonVisible = (boolean) gameModule.getPrefs().getValue(ONE_DIE_BUTTON_SETTINGS);
            twoDiceButtonVisible = (boolean) gameModule.getPrefs().getValue(TWO_DICE_BUTTON_SETTINGS);
            gameModule.getPrefs().setValue(BUTTONS_INDEX_SETTINGS, diceToolbarIndex);
            oneDieButtonSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    oneDieButton.setVisible((boolean) gameModule.getPrefs().getValue(ONE_DIE_BUTTON_SETTINGS));
                }
            });

            twoDiceButtonSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    twoDiceButton.setVisible((boolean) gameModule.getPrefs().getValue(TWO_DICE_BUTTON_SETTINGS));
                }
            });

            // Update Dice Buttons visibility
            oneDieButton.setVisible((boolean) gameModule.getPrefs().getValue(ONE_DIE_BUTTON_SETTINGS));
            twoDiceButton.setVisible((boolean) gameModule.getPrefs().getValue(TWO_DICE_BUTTON_SETTINGS));

            // ...DICE ON SCREEN DURATION
            final IntConfigurer onScreenDurationSettings = new IntConfigurer(DICE_ON_SCREEN_DURATION_SETTINGS, "Buttons on screen delay (MIN: " + MIN_ON_SCREEN_DURATION + " / MAX: " + MAX_ON_SCREEN_DURATION  + ")", onScreenDuration);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, onScreenDurationSettings);
            onScreenDuration = (int) gameModule.getPrefs().getValue(DICE_ON_SCREEN_DURATION_SETTINGS);

            onScreenDurationSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    if ((int)gameModule.getPrefs().getValue(DICE_ON_SCREEN_DURATION_SETTINGS) > MAX_ON_SCREEN_DURATION) {
                        gameModule.getPrefs().setValue(DICE_ON_SCREEN_DURATION_SETTINGS, MAX_ON_SCREEN_DURATION);
                        onScreenDuration = MAX_ON_SCREEN_DURATION;
                    } else if ((int)gameModule.getPrefs().getValue(DICE_ON_SCREEN_DURATION_SETTINGS) < MIN_ON_SCREEN_DURATION){
                        gameModule.getPrefs().setValue(DICE_ON_SCREEN_DURATION_SETTINGS, MIN_ON_SCREEN_DURATION);
                        onScreenDuration = MIN_ON_SCREEN_DURATION;
                    } else {
                        onScreenDuration = (int)gameModule.getPrefs().getValue((DICE_ON_SCREEN_DURATION_SETTINGS));
                    }
                }
            });

            // ...FRAME RATE
            final IntConfigurer frameRateSettings = new IntConfigurer(FRAME_RATE_SETTINGS, "Animation Speed (MIN: " + MIN_ANIMATION_SPEED  + " / MAX: " + MAX_ANIMATION_SPEED  + ")", animationSpeed);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, frameRateSettings);
            animationSpeed = Integer.parseInt(gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS).toString()) + MIN_VIABLE_SPEED;

            frameRateSettings.addFocusListener(new FocusListener() {
                Object initialValue;
                @Override
                public void focusGained(FocusEvent e) {
                    initialValue = gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS);
                }
                @Override
                public void focusLost(FocusEvent e) {
                    Object presentValue = gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS);
                    if (Integer.parseInt(presentValue.toString()) > MAX_ANIMATION_SPEED || Integer.parseInt(presentValue.toString()) < MIN_ANIMATION_SPEED) {
                        gameModule.getPrefs().setValue(FRAME_RATE_SETTINGS, initialValue);
                    } else {
                        gameModule.getPrefs().setValue(FRAME_RATE_SETTINGS, presentValue);
                        animationSpeed = Integer.parseInt(gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS).toString()) + MIN_VIABLE_SPEED;
                    }
                }
            });


            // ...DICE POSITION
            MAX_HORIZONTAL_OFFSET = currentMap.getView().getMaximumSize().width; // IMPLEMENT!!

            final IntConfigurer dicePositionSettings = new IntConfigurer(DICE_POSITION_SETTINGS, "Animation screen position (MIN: " + 0 + " / MAX: " + MAX_HORIZONTAL_OFFSET + ")", this.dicePositionSettings);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, dicePositionSettings);
            this.dicePositionSettings = Integer.parseInt(gameModule.getPrefs().getValue(DICE_POSITION_SETTINGS).toString());

            dicePositionSettings.addFocusListener(new FocusListener() {
                Object initialValue;

                @Override
                public void focusGained(FocusEvent e) {
                    initialValue = gameModule.getPrefs().getValue(DICE_POSITION_SETTINGS);
                }

                @Override
                public void focusLost(FocusEvent e) {
                    Object presentValue = gameModule.getPrefs().getValue(DICE_POSITION_SETTINGS);
                    if (Integer.parseInt(presentValue.toString()) > MAX_HORIZONTAL_OFFSET || Integer.parseInt(presentValue.toString()) < 0) {
                        gameModule.getPrefs().setValue(DICE_POSITION_SETTINGS, initialValue);
                    } else {
                        gameModule.getPrefs().setValue(DICE_POSITION_SETTINGS, presentValue);
                        AnimatedDice.this.dicePositionSettings = Integer.parseInt(gameModule.getPrefs().getValue(DICE_POSITION_SETTINGS).toString());
                    }
                }
            });

            // SOUNDS BUTTONS
            // ...HIDE BUTTONS
            final BooleanConfigurer shuffleSoundSettings = new BooleanConfigurer(SHUFFLE_SOUND_SETTINGS, "Shuffle sound", isShuffleSoundOn);
            final BooleanConfigurer diceSoundSettings = new BooleanConfigurer(DICE_SOUND_SETTINGS, "Dice sound", isDiceSoundOn);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, shuffleSoundSettings);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, diceSoundSettings);
            isShuffleSoundOn = (boolean) gameModule.getPrefs().getValue(SHUFFLE_SOUND_SETTINGS);
            isDiceSoundOn = (boolean) gameModule.getPrefs().getValue(DICE_SOUND_SETTINGS);
            shuffleSoundSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    isShuffleSoundOn = (boolean) gameModule.getPrefs().getValue(SHUFFLE_SOUND_SETTINGS);
                }
            });
            diceSoundSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    isDiceSoundOn = (boolean) gameModule.getPrefs().getValue(DICE_SOUND_SETTINGS);
                }
            });

            // DISPLAY NUMBERS IN CHAT
            final BooleanConfigurer resultsNumberSettings = new BooleanConfigurer(DISPLAY_NUMBERS_SETTINGS, "Display results as numbers in chat window ", displayNumbers);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, resultsNumberSettings);
            displayNumbers = (boolean) gameModule.getPrefs().getValue(DISPLAY_NUMBERS_SETTINGS);
            resultsNumberSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    displayNumbers = (boolean) gameModule.getPrefs().getValue(DISPLAY_NUMBERS_SETTINGS);
                }
            });

            // DISPLAY DICE IMAGES IN CHAT
            final BooleanConfigurer diceImagesSettings = new BooleanConfigurer(DISPLAY_DICE_IMAGES_SETTINGS, "Display dice images in chat window", displayDiceImages);
            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, diceImagesSettings);
            displayDiceImages = (boolean) gameModule.getPrefs().getValue(DISPLAY_DICE_IMAGES_SETTINGS);
            diceImagesSettings.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    displayDiceImages = (boolean) gameModule.getPrefs().getValue(DISPLAY_DICE_IMAGES_SETTINGS);
                }
            });
        }
    }

    private void loadSounds(){
        try {
            // Preload the audio data into memory
            InputStream dieSoundStream = dataArchive.getInputStream("DiceSounds/Selected1" + ".wav");
            InputStream diceSoundStream = dataArchive.getInputStream("DiceSounds/Selected2" + ".wav"); // For more than one die
            InputStream shakingDiceSoundStream = dataArchive.getInputStream("DiceSounds/Selected3" + ".wav"); // For shaking dice
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(dieSoundStream.readAllBytes()));
            dieClip = AudioSystem.getClip();
            dieClip.open(audioInputStream);
            audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(diceSoundStream.readAllBytes()));
            diceClip = AudioSystem.getClip();
            diceClip.open(audioInputStream);
            audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(shakingDiceSoundStream.readAllBytes()));
            shuffleClip = AudioSystem.getClip();
            shuffleClip.open(audioInputStream);
            dieSoundStream.close();
            diceSoundStream.close();
            shakingDiceSoundStream.close();
            audioInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playSounds(Clip clip){
        //final AudioInputStream[] audioInputStream = {null};
        //final Clip[] clip = {null};
        if (clip != null) {
            new Thread(() -> {
                clip.start();
                try {
                    synchronized (soundsLock) {
                        soundsLock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    clip.stop();
                    clip.setFramePosition(0);
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }


    private void executeRoll(int numberOfDice){
        // UNABLE BUTTONS AND HIDE DICE IF NOT YET HIDDEN
        isAnimationInProgress = true;
        this.numberOfDice = numberOfDice;
        oneDieButton.setEnabled(false);
        twoDiceButton.setEnabled(false);

        // ROLL DICE AND CREATE PIECES
        // Only rolls the dice if the current player is who called the roll. If the opposing player called the roll, display the animation based on the results passed to the global property diceRollCall
        if (thisCaller) {
            results = RollDice(numberOfDice, NUMBER_OF_SIDES);
            // After generating the results, sends a command to execute the animation in the opponents computer
            Command c = new Chatter.DisplayText(gameModule.getChatter(), playerId + " Rolling!!");
            c.append(new RunDiceAnimationCommand(this, results[0], results.length == 1 ? 0 : results[1]));
            c.execute();
            gameModule.sendAndLog(c);
        }
        if (numberOfDice == 1) {
            whiteProjectingPiece = createProjectingPiece("white", results[0], oddRound? "cache1" : "cache2");
        } else if (numberOfDice == 2){
            redProjectingPiece = createProjectingPiece("red", results[1], oddRound? "cache1" : "cache2");
            whiteProjectingPiece = createProjectingPiece("white", results[0], oddRound? "cache1" : "cache2");
        }
        // START PRELOAD OF NEXT SET OF IMAGES
        //since we finished using the current Cache for creating pieces, we can begin to feed it again
        if (oddRound)
            isFeedingCache1 = true;
        else
            isFeedingCache2 = true;

        ExecutorService executor = Executors.newSingleThreadExecutor(); // Create a single-threaded ExecutorService
        executor.submit(() -> {
            try {
                HashMap<String, HashMap<Integer, ArrayList<Image>>> cacheUsed = oddRound ? imagesCache1 : imagesCache2;
                DrawDiceFolders(cacheUsed); // Set feedingImages to FALSE after ending feed
                if (cacheUsed == imagesCache1)
                    isFeedingCache1 = false;
                else
                    isFeedingCache2 = false;
            } catch (Exception e) {
                System.out.println("Unable to preload images");
                e.printStackTrace();
            }
        });
        executor.shutdown(); // Shutdown the ExecutorService after the task is complete

        // START SOUNDS
        if (isDiceSoundOn) {
            if (numberOfDice == 1)
                playSounds(dieClip);
            else
                playSounds(diceClip);
        }

        // SET PARAMETERS
        imageDelay = (1000/ animationSpeed); // transform frame rate into milliseconds delay
        Rectangle rectangle = currentMap.getView().getVisibleRect();
        // If dicePosition (set up in preferences), which is the offset of the animation to the left,
        // is larger than the width of the window minus the width of the images, we adjust it to the maximum place to which the animation may be offset without cropping the image.
        int adjustedDicePositionSettings = (dicePositionSettings > (rectangle.width - (IMAGE_SIZE * numberOfDice))? Math.max (rectangle.width - (IMAGE_SIZE * numberOfDice), 0): dicePositionSettings);
        int min_x = rectangle.x; // leftmost point of the current visible rectangle
        int x = (min_x + adjustedDicePositionSettings); // we add the adjusted offset to the leftmost point of the window.
        int y = rectangle.y;

        // PROJECTING PIECE METHOD
        scheduler = Executors.newSingleThreadScheduledExecutor();
        placeProjectingPieces(x, y, numberOfDice);
        Runnable task2 = () -> projectImages();
        scheduler.scheduleWithFixedDelay(task2, 0, imageDelay, TimeUnit.MILLISECONDS);
    }
    private void projectImages(){
        if (numberOfDice == 2) {
            int whiteIndex = whiteProjectingPiece.nextImage();
            int redIndex = redProjectingPiece.nextImage();
            if (whiteIndex == -1 && redIndex == -1) {
                stopAnimation();
            }
        } else {
            int whiteIndex = whiteProjectingPiece.nextImage();
            if (whiteIndex == -1){
                stopAnimation();
            }
        }
        currentMap.repaint();
    }
    private void placeProjectingPieces(int x, int y, int numberOfDice){
        int diePosition = new Random().nextInt(numberOfDice); // always 0 if only one die
        if (numberOfDice == 1) {
            if (whiteProjectingPiece != null) {
                currentMap.placeAt(whiteProjectingPiece, new Point((int)(x/currentMap.getZoom()) , (int)(y / currentMap.getZoom())));
            }
        } else {
            if (whiteProjectingPiece != null && redProjectingPiece != null) {
                currentMap.placeAt(diePosition == 0 ? whiteProjectingPiece : redProjectingPiece, new Point((int)(x/currentMap.getZoom()) , (int)(y / currentMap.getZoom())));
                currentMap.placeAt(diePosition == 0 ? redProjectingPiece : whiteProjectingPiece, new Point((int)(x/currentMap.getZoom()) + ((int)(IMAGE_SIZE / currentMap.getZoom())), (int)(y / currentMap.getZoom())));
            }
        }
    }
    private void removePieces(){
        synchronized (removePieceSyncLock) {
            if (isImageVisible) { // Checks to see if the piece is still visible. It can be cleaned when the onScreenDuration time value has passed.
                isImageVisible = false;
                if (whiteProjectingPiece != null) {
                    Command remove = new RemovePiece(whiteProjectingPiece);
                    remove.execute();
                    System.gc();
                }
                if (numberOfDice == 2) {
                    if (redProjectingPiece != null) {
                        Command remove = new RemovePiece(redProjectingPiece);
                        remove.execute();
                        System.gc();
                    }
                }
            }
        }
    }

    public void stopAnimation(){
        // ENDS ANIMATION
        synchronized (soundsLock) {
            soundsLock.notify();
        }
        scheduler.shutdown();
        isAnimationInProgress = false;
        isImageVisible = true; // can only set this to true after last image is displayed, since when the button is pressed again, the behavior depends on that variable
        oddRound = !oddRound; // We change round so that in the next execution, the other cache will be used and fed.

        // CLEANS PIECES AND RESET BUTTONS
        ExecutorService executor = Executors.newSingleThreadExecutor(); // Create a single-threaded ExecutorService

        executor.submit(() -> {
            // Your task logic here
            while (oddRound ? isFeedingCache1 : isFeedingCache2) {
                Thread.yield();
            }

            oneDieButton.setEnabled(true);
            twoDiceButton.setEnabled(true);
            if (thisCaller) {
                sendResults(results);
            }
            thisCaller = false;
            currentMap.getView().repaint();

            executor.shutdown(); // Shutdown the ExecutorService after the task is completed
        });

        // REMOVE PIECES AFTER onScreenDuration if it isn't done by a new Roll.
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < (onScreenDuration * 1000)) {
            if (!isImageVisible)
                break;
        }
        synchronized (removePieceSyncLock) {
            if (isImageVisible && !isAnimationInProgress) {
                removePieces();
            }
        }
    }
    private ProjectingPiece createProjectingPiece(String die, int result, String cache){
        ArrayList<Image> images = new ArrayList<>();

        for (Object image: (cache.equals("cache1")) ? imagesCache1.get(die).get(result) : imagesCache2.get(die).get(result)){
            images.add((Image)image);
        }

        return new ProjectingPiece(images);
    }

    public void getImages(String path, HashMap<String, HashMap<Integer, ArrayList<Image>>> cache, String die, int result){
        int imageNumber = 0;

        while (true) {
            try {
                URL imageURL = dataArchive.getURL(path + String.format("die%04d", imageNumber) + ".png");
                try{
                    Image image = ImageIO.read(imageURL);
                    if (image != null)
                            cache.get(die).get(result).add(image);
                    imageNumber++;
                } catch (IOException e){
                    e.printStackTrace();
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    private void DrawDiceFolders(HashMap<String, HashMap<Integer, ArrayList<Image>>> cache){
        // Clears the previously preloaded images
        // If round is odd, clears imagesCache1, since it was used to create the pieces in this round before the call to this method.
        for (int i = 1; i <= NUMBER_OF_SIDES; i++){
                cache.get("white").get(i).clear();
                cache.get("red").get(i).clear();
        }
        System.gc();
        // RECOVER THE LAST ANIMATION VARIANT FOR EACH COLOR. O MEANS NONE.
        int lastRedHesitantAnim = (boolean) lastAnimationUsed.get("red")[1] ? (int) lastAnimationUsed.get("red")[0] : 0;
        int lastWhiteHesitantAnim = (boolean) lastAnimationUsed.get("white")[1] ? (int) lastAnimationUsed.get("white")[0] : 0;
        int lastRedAnim = !(boolean) lastAnimationUsed.get("red")[1] ? (int) lastAnimationUsed.get("red")[0] : 0;
        int lastWhiteAnim = !(boolean) lastAnimationUsed.get("white")[1] ? (int) lastAnimationUsed.get("white")[0] : 0;

        double hesitantProb = hesitantDieProbability * ((double)NUMBER_OF_HESITANT_DIE_ANIMATIONS/(double)NUMBER_OF_DIE_ANIMATIONS); // We must update it, since hesitantDieProbability may be changed in preference
        int hesitantDie = 3; // 0 is white, 1 is red and 3 is none
        if((boolean) lastAnimationUsed.get("white")[1] || ((boolean) lastAnimationUsed.get("red")[1])) { // index 1 checks a boolean that is true if the last animation used for that die was a hesitant die animation. Only considers the red die case if it was included in the las toll.
            hesitantProb = hesitantProb * 0.3; // reduces the probability of a sequential hesitant die animation
        }

        Random random = new Random();
        // BEGIN FEEDING HESITANT DIE IMAGES
        if (random.nextDouble() < hesitantProb){ //Choose among hesitant die animations for red or white die
            hesitantDie = random.nextInt(NUMBER_OF_DICE); // The die that will have the hesitant animation. 0 is white and 1 is red
            int animNumber; // The animation variation to be used
            do {
                animNumber = random.nextInt(NUMBER_OF_HESITANT_DIE_ANIMATIONS) + 1; // there is no animation with index 0
            } while ((lastRedHesitantAnim != 0 && animNumber == lastRedHesitantAnim) || (lastWhiteHesitantAnim != 0 && animNumber ==lastWhiteHesitantAnim)); // prevents immediate repetition of animation if any die used that variation in the last roll

            String folderPrefix = (hesitantDie == 0) ? HESITANT_WHITE_DIE_FOLDER_PATH : HESITANT_RED_DIE_FOLDER_PATH;

            for (int i = 1; i <= NUMBER_OF_SIDES; i++) {
                ArrayList<Integer> animVariation = (hesitantDie == 0) ?
                        whiteHesitantDieFolderBuilder.get(animNumber).get(i) :
                        redHesitantDieFolderBuilder.get(animNumber).get(i);

                StringBuilder path = new StringBuilder();
                path.append(folderPrefix).append((hesitantDie == 0) ? "W" : "R").append(animNumber).append("_")
                        .append(animVariation.get(random.nextInt(animVariation.size()))).append("_").append(i).append("/");
                getImages(path.toString(), cache, (hesitantDie == 0) ? "white" : "red", i);
            }
            if (hesitantDie == 0) {
                lastAnimationUsed.put("white", new Object[]{animNumber, true});
            } else {
                lastAnimationUsed.put("red", new Object[]{animNumber, true});
            }
        }

        // BEGIN FEEDING NORMAL DIE IMAGES
        int redAnimNumber;
        int whiteAnimNumber;

        do {
            redAnimNumber = random.nextInt(NUMBER_OF_DIE_ANIMATIONS) + 1; // there is no animation with index 0
            whiteAnimNumber = random.nextInt(NUMBER_OF_DIE_ANIMATIONS) + 1;
        } while ((lastRedAnim != 0 && (redAnimNumber == lastRedAnim || whiteAnimNumber == lastRedAnim)) || (lastWhiteAnim != 0 && (whiteAnimNumber == lastWhiteAnim || redAnimNumber == lastWhiteAnim)) || (redAnimNumber == whiteAnimNumber)); // prevents immediate repetition of animation and equal animations

        if (hesitantDie != 1) { // RED DIE PRELOAD
            for (int i = 1; i <= NUMBER_OF_SIDES; i++) {
                StringBuilder path = new StringBuilder();
                path.append(RED_DIE_FOLDER_PATH).append("R").append(redAnimNumber).append("_")
                        .append(i).append("/");

                getImages(path.toString(), cache, "red", i);
            }
            lastAnimationUsed.put("red", new Object[]{redAnimNumber, false});
        }
        if (hesitantDie != 0) { // WHITE DIE PRELOAD
            for (int i = 1; i <= NUMBER_OF_SIDES; i++) {
                StringBuilder path = new StringBuilder();
                path.append(WHITE_DIE_FOLDER_PATH).append("W").append(whiteAnimNumber).append("_")
                        .append(i).append("/");

                getImages(path.toString(), cache, "white", i);
            }
            lastAnimationUsed.put("white", new Object[]{whiteAnimNumber, false});
        }
    }
    private void sendResults(int[] results){
        try {
            String redDie = "";
            String redDieNumber = " ";
            String whiteDie = "";
            String whiteDieNumber = "";
            if (results.length == 2){
                URL redDieIconURL = dataArchive.getURL(ICONS_IMAGES_PATH + "red" + results[1] + ".png");
                if (displayDiceImages)
                    redDie = "<img src='" + redDieIconURL + "'>";
                if (displayNumbers)
                    redDieNumber = ",</b> " + "<style='font-size: 14; color: BF0000;'> <b>" + results[1] + "</b> </style>";
            }
            URL whiteDieIconURL = dataArchive.getURL(ICONS_IMAGES_PATH + "white" + results[0] + ".png");
            if (displayDiceImages)
                whiteDie = "<img src='" + whiteDieIconURL + "'>";
            if (displayNumbers)
                whiteDieNumber = "</b> <style='font-size: 14'> <b>" + + results[0];

            String message = "- | <b>" + playerId + "  " + whiteDieNumber + redDieNumber + whiteDie + " " +  redDie ;

            Command c = new Chatter.DisplayText(gameModule.getChatter(), message);
            c.execute();
            gameModule.sendAndLog(c);
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private int[] RollDice(int numberOfDice, int numberOfSides){
        int[] unalteredRolls = DR(numberOfDice,numberOfSides);
        int[] alteredRolls = new int[unalteredRolls.length]; // Rolls after bias application

        for (int i = 0; i < unalteredRolls.length; i++){
            alteredRolls[i] = (unalteredRolls[i] + Math.abs(mouseBiasFactor[i] % 6));
            if (alteredRolls[i] > 6)
                alteredRolls[i] = alteredRolls[i] - 6;
        }
        return alteredRolls;
    }
    protected int[] DR(int nDice, int nSides) {
        int[] rawRolls = new int[nDice];

        for(int i = 0; i < nDice; ++i) {
            Random ran = gameModule.getRNG();
            int roll = ran.nextInt(nSides) + 1;
            rawRolls[i] = roll;
        }

        return rawRolls;
    }

    public void removeFrom(Buildable parent) {
        if (parent instanceof GameModule) {
            GameModule gameModule = (GameModule) parent;

            // Remove the button from the toolbar
            gameModule.getToolBar().remove(oneDieButton);
            gameModule.getToolBar().remove(twoDiceButton);

            // Remove Command encoders
            ((GameModule) parent).removeCommandEncoder(this);
            ((GameModule) parent).getGameState().removeGameComponent(this);
        }
    }

    // Creates a mask that indicates what animation indexes (the number just to the right of the die color letter in the folder) bring hesitant die animations
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> CreateHesitantDieFolderBuilder(String path){
        HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> folderNameBuilder = new HashMap<>();
        try {
            InputStream inputStream = dataArchive.getInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null){
                String[] parts = line.split("/");
                int animationIndex = Integer.parseInt(parts[0]);
                int result = Integer.parseInt(parts[2]);
                int rejectedResult = Integer.parseInt(parts[1]);
                if (!folderNameBuilder.containsKey(animationIndex)) {
                    folderNameBuilder.put(animationIndex, new HashMap<>()); // is a hesitant die animation
                    for (int i = 1; i <= 6; i++) {
                        folderNameBuilder.get(animationIndex).put(i, new ArrayList<Integer>());
                    }
                }
                folderNameBuilder.get(animationIndex).get(result).add(rejectedResult);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return folderNameBuilder;
    }

    public class DelayedActionButton extends JButton {
        private boolean mouseButtonPressed = false;
        private final ActionListener delayedActionListener;

        public DelayedActionButton(String buttonText, Icon icon, ActionListener delayedActionListener) {
            super(icon);
            this.delayedActionListener = delayedActionListener;
            this.setText(buttonText);
            setupMouseListener();
        }

        private void setupMouseListener() {
            addMouseListener(new MouseAdapter() {
                private Timer timer;
                @Override
                public void mousePressed(MouseEvent e) {
                    super.mousePressed(e);
                    synchronized (removePieceSyncLock) {
                        removePieces();
                    }
                    mouseButtonPressed = true;
                    if (!isAnimationInProgress && isEnabled()) { // doesn't execute when pressed to hide the dices (dice images are still visible)
                        if (isShuffleSoundOn)
                            playSounds(shuffleClip);
                        mouseBiasFactor = new int[NUMBER_OF_DICE];
                        Arrays.fill(mouseBiasFactor, 1);
                        JButton button = (JButton) e.getSource();
                        ActionListener timerAction = new ActionListener() {
                            private long colorCounter = 10;
                            private int counter = 0;
                            private long startTime = System.currentTimeMillis();

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                if (elapsedTime >= 4000) {
                                    timer.stop();
                                } else {
                                    if (elapsedTime > colorCounter) {
                                        button.setBackground(
                                                button.getBackground() == Color.YELLOW ? Color.WHITE : Color.YELLOW);
                                        colorCounter += 100;
                                    }

                                    mouseBiasFactor[counter] += MouseInfo.getPointerInfo().getLocation().x
                                            + MouseInfo.getPointerInfo().getLocation().y;

                                    if (mouseBiasFactor[counter] > 10000)
                                        mouseBiasFactor[counter] = mouseBiasFactor[counter] - 10000;

                                    counter = (counter + 1) % mouseBiasFactor.length;
                                }
                            }
                        };

                        timer = new Timer(10, timerAction);
                        timer.start();
                    }
                }


                @Override
                public void mouseReleased(MouseEvent e) {
                    super.mouseReleased(e);
                    Color originalColor = new Color(238,238,238);
                    JButton button = (JButton) e.getSource();

                    if (mouseButtonPressed && isEnabled()) {
                        try{ // Necessary to allow sound to begin and notify to work properly?
                            Thread.sleep(100);
                        } catch (InterruptedException a){
                            a.printStackTrace();
                        }
                        synchronized (soundsLock){
                            soundsLock.notify();
                        }
                        try{ // Necessary to allow sound to begin and notify to work properly?
                            Thread.sleep(100);
                        } catch (InterruptedException a){
                            a.printStackTrace();
                        }
                        if (timer != null) {
                            timer.stop();
                        }
                        if (e.getButton() == MouseEvent.BUTTON1) { // if left button, display animation
                            delayedActionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
                        } else {
                            if (e.getSource() == oneDieButton){
                                results = RollDice(1, 6);
                                sendResults(results);
                            }
                            if (e.getSource() == twoDiceButton){
                                results = RollDice(2, 6);
                                sendResults(results);
                            }
                        }
                    }
                    button.setBackground(originalColor);
                    mouseButtonPressed = false;
                }
            });
        }
    }

    protected static class RunDiceAnimationCommand extends Command {
        private final AnimatedDice animatedDice;
        private final int whiteResult;
        private final int redResult;
        private int[] results;

        public RunDiceAnimationCommand(AnimatedDice animatedDice, int whiteResult, int redResult) {
            this.animatedDice = animatedDice;
            this.whiteResult = whiteResult;
            this.redResult = redResult;
        }

        protected void executeCommand() {
            if (animatedDice.oneDieButton.isEnabled() || animatedDice.twoDiceButton.isEnabled()){
                if (redResult == 0){
                    this.results = new int[1];
                    this.results[0] = whiteResult;
                } else {
                    this.results = new int[2];
                    this.results[0] = whiteResult;
                    this.results[1] = redResult;
                }
                animatedDice.results = this.results;
                synchronized (animatedDice.removePieceSyncLock) {
                    animatedDice.removePieces();
                }
                animatedDice.executeRoll(results.length);
            }
        }
        public int getWhiteResult(){
            return whiteResult;
        }
        public int getRedResult(){
            return redResult;
        }

        protected Command myUndoCommand() {
            return null;
        }
    }

    public String encode(Command c) {
        return !(c instanceof RunDiceAnimationCommand) ? null : COMMAND_PREFIX + ((RunDiceAnimationCommand) c).getWhiteResult() + ((RunDiceAnimationCommand) c).getRedResult();
    }
    public Command decode(String s) {
        return !s.startsWith(COMMAND_PREFIX) ? null : new RunDiceAnimationCommand(this,
                Integer.parseInt(s.substring(COMMAND_PREFIX.length(), COMMAND_PREFIX.length() + 1)),
                Integer.parseInt(s.substring(COMMAND_PREFIX.length() + 1, COMMAND_PREFIX.length() + 2)));
    }

    public class ProjectingPiece extends BasicPiece{
        private int imageIndex;
        private final ArrayList<Image> images;

        public ProjectingPiece(ArrayList<Image> images){
            super();
            this.images = images;
        }

        @Override
        public void draw(Graphics g, int x, int y, Component obs, double zoom) {
            super.draw(g, x, y, obs, zoom);

            // Draw the image at the specified (x, y) coordinates using the current image index
            Image image = images.get(imageIndex);
            g.drawImage(image, x, y, obs);
        }

        // Method to change the current image index
        public int setImageIndex(int newIndex) {
            if (newIndex >= 0 && newIndex < images.size()) {
                imageIndex = newIndex;
            } else {
                return -1;
            }
            return 1;
        }

        public int nextImage(){
            return setImageIndex(imageIndex + 1);
        }
    }
}



package my_custom_component;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.command.RemovePiece;
import VASSAL.configure.IntConfigurer;
import VASSAL.counters.BasicPiece;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class AnimatedDice extends AbstractConfigurable implements CommandEncoder, Buildable{
    private GameModule gameModule;
    private static final String IMAGES_FOLDER = "my_custom_component/images";
    private static final String SOUNDS_FOLDER = "my_custom_component/sounds";
    private static int filesInFolder;
    final String ANIMATED_DICE_PREFERENCES = "Animated 3D Dice";
    final String FRAME_RATE_SETTINGS = "frameRateSettings";
    private static final int IMAGE_SIZE = 300;
    private boolean isImageVisible; // establish if last dice frame is still visible in the screen so that the button is in Hide mode.
    private boolean isAnimationInProgress = false; // prevents mousePress action before animation finishes.
    private JButton customButton;
    private int[] mouseBiasFactor; // Set of ints generated by mouse movement which will influence dice results
    private int nDice; // number of dices rolled
    private int nSides; // number of sides of a die
    private long imageDelay; // The number of MILLISECONDS between the display actions.
    private int currentFrame;
    private int frameRate;
    private final int MAX_FRAME_RATE = 60;
    private final int MIN_FRAME_RATE = 35;
    private ScheduledExecutorService scheduler; // Controls the frame rate of the displayed images
    private Image[] images; // to be fed with the dice images that will be drawn on the pieces
    private byte[] dieAudioData;
    private byte[] diceAudioData;
    private byte[] shakingDiceAudioData;
    private boolean shouldStopFlag;
    private Cursor dieCursor;
    private boolean actionInProgress = false;
    private BasicPiece[] pieces; // pieces are added to this array to be displayed in order
    private final Map currentMap;

    public AnimatedDice(){
        isImageVisible = false;
        currentMap = GameModule.getGameModule().getComponentsOf(Map.class).get(0);
        gameModule = GameModule.getGameModule();
        filesInFolder = countFilesInFolder(System.getProperty("user.dir") + "/target/classes/" + IMAGES_FOLDER + "/DiceImages/");
        currentFrame = 0;
        nDice = 3;
        nSides = 6;

        // GET RESOURCES
        loadSounds(); // Preloads sounds for dices
        URL dieCursorImageURL = getClass().getResource("/" + IMAGES_FOLDER + "/" + "DieCursor.png");
        try {
            BufferedImage dieCursorImage = ImageIO.read(dieCursorImageURL);
            dieCursor = Toolkit.getDefaultToolkit().createCustomCursor(dieCursorImage, new Point(0,0), "Custom Die Cursor");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

    }


    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof GameModule) {
            gameModule = (GameModule) parent;

            // Create your button instance
            customButton = new DelayedActionButton("Roll Dice", new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleImagesVisibility();
                }
            });
            customButton.setToolTipText("Displays Image on map");

            // Add the button to the toolbar
            gameModule.getToolBar().add(customButton);

            // ADD SETTINGS TO PREFERENCE WINDOW

            final IntConfigurer frameRateSettings = new IntConfigurer(FRAME_RATE_SETTINGS, "Frame Rate (MAX: " + MAX_FRAME_RATE + " / MIN: " + MIN_FRAME_RATE + ")", 45);

            gameModule.getPrefs().addOption(ANIMATED_DICE_PREFERENCES, frameRateSettings);
            frameRate = Integer.parseInt(gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS).toString());

            frameRateSettings.addFocusListener(new FocusListener() {
                Object initialValue;
                @Override
                public void focusGained(FocusEvent e) {
                    initialValue = gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS);
                }
                @Override
                public void focusLost(FocusEvent e) {
                    Object presentValue = gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS);
                    if (Integer.parseInt(presentValue.toString()) > MAX_FRAME_RATE || Integer.parseInt(presentValue.toString()) < MIN_FRAME_RATE) {
                            gameModule.getPrefs().setValue(FRAME_RATE_SETTINGS, initialValue);
                    } else {
                            gameModule.getPrefs().setValue(FRAME_RATE_SETTINGS, presentValue);
                    }
                }
            });
        }
    }

    private void loadSounds(){
        InputStream dieSoundsURL = getClass().getResourceAsStream("/" + SOUNDS_FOLDER + "/" + String.format("Selected1" + ".wav"));
        InputStream diceSoundsURL = getClass().getResourceAsStream("/" + SOUNDS_FOLDER + "/" + String.format("Selected2" + ".wav")); // For more than one die
        InputStream shakingDiceSoundsURL = getClass().getResourceAsStream("/" + SOUNDS_FOLDER + "/" + String.format("Selected3" + ".wav")); // For shaking dice

        // Preload the audio data into memory
        try {
            dieAudioData = dieSoundsURL.readAllBytes();
            diceAudioData = diceSoundsURL.readAllBytes();
            shakingDiceAudioData = shakingDiceSoundsURL.readAllBytes();
        } catch (IOException e){
            System.out.println("Exception reading sounds data");
            e.printStackTrace();
        }
        try {
            dieSoundsURL.close(); // Close the stream
            diceSoundsURL.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private void playSounds(byte[] audioData){
        try{
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioData));
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            new Thread(() -> {
                clip.start();
                while (clip.getFramePosition() < clip.getFrameLength()){
                    if (shouldStopFlag)
                        clip.close();
                    Thread.yield();
                }
                clip.close();
                Thread.currentThread().interrupt();
            }).start();
            try{  // Works without the try block, but seems to delay the first use of sounds
                audioInputStream.close();
            } catch(IOException e){
                e.printStackTrace();
            }
        } catch (Exception e){
            System.out.println("Exception thrown");
            e.printStackTrace();
        }
        shouldStopFlag = false;
    }


    private void toggleImagesVisibility(){
        if (isImageVisible){
            customButton.setEnabled(false);
            hideImage(pieces[pieces.length - 1]); // Hide last frame, which remained visible
            isImageVisible = false;
            // We definitely remove each piece so that no artifacts are presented on screen.
            for (BasicPiece piece: pieces){
                Command remove = new RemovePiece(piece);
                remove.execute();
            }
            customButton.setText("Roll Dice");
            customButton.setEnabled(true);
            currentMap.getView().repaint();
        } else {
            isAnimationInProgress = true;
            customButton.setEnabled(false);
            frameRate = Integer.parseInt(gameModule.getPrefs().getValue(FRAME_RATE_SETTINGS).toString()); // Read the frame rate from preferences
            imageDelay = (1000/frameRate); // transform frame rate into milliseconds delay
            RollDices (nDice, nSides);
            getImages(); // We must populate the images array before calling createPieces.
            createPieces();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            playSounds(dieAudioData);
            scheduler.scheduleAtFixedRate(this::displayImage, 0, imageDelay, TimeUnit.MILLISECONDS);
        }
    }

    public void stopImageDisplay(){
        scheduler.shutdown();
        try{
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)){
                scheduler.shutdownNow();
                customButton.setEnabled(true);
                isAnimationInProgress = false;
            }
        } catch (InterruptedException ex){
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void displayImage(){
        if (currentMap != null){
            if (currentFrame == pieces.length) {
                stopImageDisplay();
                currentFrame = 0;
                isImageVisible = true; // can only set this to true after last image is displayed, since when the button is pressed again, the behavior depends on that variable.
                customButton.setText("Hide Dice");
                return;
            }
            int xCoordinate = 0;
            int yCoordinate = 0;

            currentMap.placeAt(pieces[currentFrame], new Point(xCoordinate,yCoordinate));
            if (currentFrame > 1){
                currentMap.removePiece(pieces[currentFrame - 1]);
            }

            currentFrame = (currentFrame + 1);
        }
    }

    private void hideImage(BasicPiece piece) {
        if (currentMap != null) {
            currentMap.removePiece(piece);
            currentMap.repaint();
        }
    }

    private void createPieces(){
        int numberOfPieces = images.length;
        pieces = new BasicPiece[numberOfPieces];
        for (int i = 0; i < numberOfPieces; i++){
            final int index = i; // make index final, so it can be accessed from the inner class
            BasicPiece piece = new BasicPiece() {
                private final Image image = images[index];
                @Override
                public void draw(Graphics g, int x, int y, Component obs, double zoom) {
                    super.draw(g, x, y, obs, zoom);
                    // Draw the image at the specified (x, y) coordinates
                    g.drawImage(image, x, y, obs);
                }
            };
            pieces[i] = piece;
        }
    }

    // Retrieve dice images from the proper folder and places them into the images Array;
    public void getImages(){
        images = new Image[filesInFolder];
        for (int i = 0; i < filesInFolder; i++) {
            try {
                URL imageURL = getClass().getResource("/" + IMAGES_FOLDER + "/DiceImages/" + String.format("dices%04d", i) + ".png");
                if (imageURL != null) {
                    images[i] = (ImageIO.read(imageURL));
                } else {
                    throw new IOException("Image file not found.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void RollDices (int numberOfDice, int numberOfSides){
        int[] unalteredRolls = DR(numberOfDice,numberOfSides);
        int[] alteredRolls = new int[unalteredRolls.length]; // Rolls after bias application

        for (int i = 0; i < unalteredRolls.length; i++){
            alteredRolls[i] = (unalteredRolls[i] + mouseBiasFactor[i] % 6);
            if (alteredRolls[i] > 6)
                alteredRolls[i] = alteredRolls[i] - 6;
        }

        StringBuilder report = new StringBuilder();
        report.append("Original Rolls: ");
        for (int j = 0; j < nDice; ++j){
            report.append(unalteredRolls[j]);
            if (j < nDice - 1)
                report.append(", ");
        }
        report.append("Altered Rolls: ");
        for (int k = 0; k < nDice; ++k){
            report.append(alteredRolls[k]);
            if (k < nDice - 1)
                report.append(", ");
        }

        Command c = report.length() == 0 ? new NullCommand() : new Chatter.DisplayText(GameModule.getGameModule().getChatter(), report.toString());
        ((Command)c).execute();
        GameModule.getGameModule().sendAndLog(c);
    }
    protected int[] DR(int nDice, int nSides) {
        int[] rawRolls = new int[nDice];

        for(int i = 0; i < nDice; ++i) {
            Random ran = new Random();
            int roll = ran.nextInt(nSides) + 1;
            rawRolls[i] = roll;
        }

        return rawRolls;
    }

    public void removeFrom(Buildable parent) {
        if (parent instanceof GameModule) {
            GameModule gameModule = (GameModule) parent;

            // Remove the button from the toolbar
            gameModule.getToolBar().remove(customButton);
        }
    }

    private static int countFilesInFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println(folderPath);
            System.out.println("The specified folder does not exist or is not a directory.");
            return 0;
        }

        String[] files = folder.list();
        if (files == null) {
            System.out.println("Error listing files in the folder.");
            return 0;
        }

        return files.length;
    }


    @Override
    public Command decode(String s) {
        return null;
    }
    @Override
    public String encode(Command command){
        return null;
    }

    @Override
    public String[] getAttributeNames(){
        return new String[]{"AnimatedDice"};
    }
    @Override
    public void setAttribute(String attribute, Object object){
        attribute = "AnimatedDice";
        object = new AnimatedDice();
    }

    @Override
    public String getAttributeValueString(String value){
        return "AnimatedDice";
    }
    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{"3D dices"};
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class[]{AnimatedDice.class};
    }

    @Override
    public HelpFile getHelpFile(){
        HelpFile help = new HelpFile();
        return help;
    }
    @Override
    public Class<?>[] getAllowableConfigureComponents() {
        return new Class[]{AnimatedDice.class};
    }

    public class DelayedActionButton extends JButton {
        private boolean mouseButtonPressed = false;
        private ActionListener delayedActionListener;

        public DelayedActionButton(String buttonText, ActionListener delayedActionListener) {
            super();
            this.delayedActionListener = delayedActionListener;
            this.setText(buttonText);
            setupMouseListener();
        }

        private void setupMouseListener() {
            addMouseListener(new MouseAdapter() {
                java.util.Timer timer;
                @Override
                public void mousePressed(MouseEvent e) {
                    System.out.println("NEW ROLL");
                    super.mousePressed(e);
                    mouseButtonPressed = true;
                    if (!isImageVisible && !isAnimationInProgress) { // doesn't execute when pressed to hide the dices (dice images are still visible)
                        setCursor(dieCursor);
                        playSounds(shakingDiceAudioData);
                        mouseBiasFactor = new int[nDice]; // We'll the number of factors correspondent to the number of dice;
                        Arrays.fill(mouseBiasFactor, 1);
                        final int[] counter = new int[]{0}; // Use of single element array in order to be able to change it inside runnable.

                        timer = new java.util.Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                System.out.println("Mouse Position: " + MouseInfo.getPointerInfo().getLocation());

                                mouseBiasFactor[counter[0]] += MouseInfo.getPointerInfo().getLocation().x + MouseInfo.getPointerInfo().getLocation().y;
                                if (mouseBiasFactor[counter[0]] > 10000)
                                    mouseBiasFactor[counter[0]] = mouseBiasFactor[counter[0]] - 10000;

                                if (counter[0] == mouseBiasFactor.length - 1) {
                                    counter[0] = 0;
                                } else {
                                    counter[0] = counter[0] + 1;
                                }
                                System.out.println("Bias " + counter[0] + " = " + mouseBiasFactor[counter[0]]);
                            }
                        }, 0, 10);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    super.mouseReleased(e);
                    setCursor(Cursor.getDefaultCursor());
                    if (mouseButtonPressed && isEnabled()) {
                        shouldStopFlag = true;
                        timer.cancel();
                        delayedActionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
                    }
                    mouseButtonPressed = false;
                }
            });
        }
    }

}


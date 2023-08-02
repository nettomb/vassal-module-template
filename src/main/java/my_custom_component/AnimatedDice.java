package my_custom_component;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.counters.BasicPiece;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AnimatedDice extends AbstractBuildable implements CommandEncoder, Buildable{
    private GameModule gameModule;
    private static final String DICE_IMAGE_FOLDER = "my_custom_component/images";
    private static int filesInFolder;
    private static final int IMAGE_SIZE = 300;
    private boolean isImageVisible;
    private JButton customButton;
    private final long FRAME_RATE; // The number of MILLISECONDS between the display actions.
    int currentFrame;
    private ScheduledExecutorService scheduler; // Controls the frame rate of the displayed images
    private Image[] images; // to be fed with the dice images that will be drawn on the pieces
    private BasicPiece[] pieces; // pieces are added to this array to be displayed in order
    private final Map currentMap;

    public AnimatedDice(){
        isImageVisible = false;
        currentMap = GameModule.getGameModule().getComponentsOf(Map.class).get(0);
        gameModule = GameModule.getGameModule();
        filesInFolder = countFilesInFolder(System.getProperty("user.dir") + "/target/classes/" + DICE_IMAGE_FOLDER);
        FRAME_RATE = 1000/30;
        currentFrame = 0;
    }

    public static void main(String[] args) {

    }


    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof GameModule) {
            gameModule = (GameModule) parent;

            // Create your button instance
            customButton = new JButton("Show Piece");
            customButton.setToolTipText("Displays Image on map");

            customButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleImagesVisibility();
                }
            });

            // Add the button to the toolbar
            gameModule.getToolBar().add(customButton);
        }
    }

    /*private void toggleImagesVisibility(){
        if (isImageVisible){
            for (BasicPiece piece: pieces) {
                hideImage(piece);
            }
            isImageVisible = false;
            customButton.setText("Show Image");
        } else {
            getImages(); // We must populate the images array before calling createPieces.
            createPieces();
            for (int i=0; i < pieces.length; i++) {
                displayImage(pieces[i]);
            }
            isImageVisible = true;
            customButton.setText("Hide Image");
        }
    }

    private void displayImage(BasicPiece piece){
        if (currentMap != null){
            int xCoordinate = 0;
            int yCoordinate = 0;
            currentMap.placeAt(piece, new Point(xCoordinate,yCoordinate));
        }
    }*/

    private void toggleImagesVisibility(){
        if (isImageVisible){
            customButton.setEnabled(false);
            for (BasicPiece piece: pieces) {
                hideImage(piece);
            }
            isImageVisible = false;
            customButton.setText("Show Image");
            customButton.setEnabled(true);
        } else {
            customButton.setEnabled(false);
            getImages(); // We must populate the images array before calling createPieces.
            createPieces();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::displayImage, 0, FRAME_RATE, TimeUnit.MILLISECONDS);
        }
    }

    public void stopImageDisplay(){
        scheduler.shutdown();
        try{
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)){
                scheduler.shutdownNow();
                customButton.setEnabled(true);
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
                isImageVisible = true;
                customButton.setText("Hide Image");
                return;
            }
            int xCoordinate = 0;
            int yCoordinate = 0;
            currentMap.placeAt(pieces[currentFrame], new Point(xCoordinate,yCoordinate));
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
            System.out.println("piece at slot " + i + "is named: " + piece);
        }
    }

    // Retrieve dice images from the proper folder and places them into the images Array;
    public void getImages(){
        images = new Image[filesInFolder];
        System.out.println("# images: " + filesInFolder);
        for (int i = 0; i < filesInFolder; i++) {
            try {
                URL imageURL = getClass().getResource("/" + DICE_IMAGE_FOLDER + "/" + String.format("dices%04d", i) + ".png");
                System.out.println(String.format("/dices%04d.png", i));
                System.out.println("URL: " + imageURL);
                if (imageURL != null) {
                    images[i] = (ImageIO.read(imageURL));
                    System.out.println(images[i]);
                } else {
                    throw new IOException("Image file not found.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
    public String[] getAttributeNames(){
        return new String[]{};
    }
    @Override
    public void setAttribute(String attribute, Object object){

    }

    @Override
    public String getAttributeValueString(String value){
        return "";
    }

    @Override
    public Command decode(String s) {
        return null;
    }
    @Override
    public String encode(Command command){
        return null;
    }

}



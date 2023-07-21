package my_custom_component;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.AddPiece;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.RemovePiece;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Stack;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;

public final class AnimatedDice extends AbstractBuildable implements CommandEncoder, Buildable{
    private GameModule gameModule;
    private String buttonText = "Show Piece";
    private static final String DICE_IMAGE_FOLDER = "my_custom_component/images";
    private static final int IMAGE_SIZE = 300;

    private PieceSlot imagePieceSlot;
    private boolean isImageVisible;
    private JButton customButton;

    private Stack stack;
    private BasicPiece displayPiece; // The piece that will be used to display the images of dices

    private Map currentMap;

    public AnimatedDice(){
        isImageVisible = false;
        imagePieceSlot = null;
        stack = new Stack();
        currentMap = GameModule.getGameModule().getComponentsOf(Map.class).get(0);
        gameModule = GameModule.getGameModule();
    }

    public static void main(String[] args) {

    }


    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof GameModule) {
            gameModule = (GameModule) parent;

            // Create your button instance
            customButton = new JButton(buttonText);
            customButton.setToolTipText("Displays Image on map");

            customButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleImageVisibility(displayPiece);
                }
            });

            // Add the button to the toolbar
            gameModule.getToolBar().add(customButton);
        }
    }

    private void toggleImageVisibility(BasicPiece piece){
        if (isImageVisible){
            hideImage(displayPiece);
        } else {
            createPiece();
            displayImage(displayPiece);
        }
    }

    private void createPiece(){

        displayPiece = new BasicPiece() {
            @Override
            public void draw(Graphics g, int x, int y, Component obs, double zoom) {
                super.draw(g, x, y, obs, zoom);
                Image image = getImage();
                // Draw the image at the specified (x, y) coordinates
                if (image != null) {
                    g.drawImage(image, x, y, obs);
                }
            }

            public Image getImage(){
                // Load the image from the "image" folder (make sure the image.jpg file is present in the image folder)
                try
                {
                    //InputStream is = getClass().getClassLoader().getResourceAsStream("/" + DICE_IMAGE_FOLDER + "/dices.jpg");
                    URL imageURL = getClass().getResource("/" + DICE_IMAGE_FOLDER + "/dices.jpg");
                    if (imageURL != null) {
                        Image image = ImageIO.read(imageURL);
                        return image;
                    } else {
                        System.out.println("Image file not found");
                        System.out.println("Resource path: " + imageURL.getPath());
                        throw new IOException("Image file not found.");
                    }
                } catch(IOException e)
                {
                    e.printStackTrace();
                }
                return null;
            }
        };

        int xCoordinate = 0;
        int yCoordinate = 0;
        currentMap.placeAt(displayPiece, new Point(xCoordinate,yCoordinate));

        //gameModule.save(); // see if it is necessary

        PieceSlot pieceSlot = new PieceSlot(displayPiece);

        currentMap.placeAt(displayPiece, new Point(xCoordinate,yCoordinate));
    }

    private void displayImage(BasicPiece piece){
        if (imagePieceSlot == null){
            imagePieceSlot = new PieceSlot(piece);

            if (currentMap != null){
                AddPiece addPiece = new AddPiece (piece);
                addPiece.execute();

                isImageVisible = true;
                customButton.setText("Hide Image");
            }
        }
    }

    private void hideImage(BasicPiece piece) {
        if (imagePieceSlot != null) {
            imagePieceSlot = null;

            if (currentMap != null) {
                RemovePiece removePiece = new RemovePiece (piece);
                removePiece.execute();

                isImageVisible = false;
                customButton.setText("Show Image");
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



    @Override
    public String[] getAttributeNames(){
        return new String[]{};
    }
    @Override
    public void setAttribute(String attribute, Object object){

    }

    @Override
    public String getAttributeValueString(String value){
        return new String ("");
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



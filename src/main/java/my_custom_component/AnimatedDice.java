package my_custom_component;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.properties.PromptProperty;
import VASSAL.build.module.properties.StringConfigurer;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.Configurer;
import VASSAL.tools.image.ImageUtils;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




public class AnimatedDice extends AbstractConfigurable implements CommandEncoder {

    private static final String DICE_IMAGE_FOLDER = "DiceImages";
    private static final int IMAGE_SIZE = 100;
    private static final int FRAME_RATE = 30;

    private final Timer timer;
    private final JLabel diceImageLabel;

    private List<BufferedImage> diceImages;
    private int currentIndex;

    public AnimatedDice() {
        diceImageLabel = new JLabel();
        diceImageLabel.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));

        timer = new Timer(1000 / FRAME_RATE, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                displayNextImage();
            }
        });

        loadDiceImages();
    }

    private void loadDiceImages() {
        diceImages = new ArrayList<>();

        // Get the working directory path
        String workingDirectory = GameModule.getGameModule().getDataArchive().getName(); //// ALTERED

        // Create the folder path for the dice images
        String diceImagesPath = workingDirectory + File.separator + DICE_IMAGE_FOLDER;

        // Load the dice images from the folder
        File diceImagesFolder = new File(diceImagesPath);
        File[] imageFiles = diceImagesFolder.listFiles();

        if (imageFiles != null) {
            for (File file : imageFiles) {
                if (file.isFile()) {
                    BufferedImage image = ImageIO.read(file); //// THROW EXCEPTION
                    if (image != null && ImageUtils.isCompatibleImage(image)) {
                        diceImages.add(image);
                    }
                }
            }
        }

        // Initialize the current index
        currentIndex = 0;
    }

    private void displayNextImage() {
        if (currentIndex >= diceImages.size()) {
            return;
        }

        BufferedImage image = diceImages.get(currentIndex);
        ImageIcon icon = new ImageIcon(image.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE, Image.SCALE_SMOOTH));
        diceImageLabel.setIcon(icon);

        if (currentIndex < diceImages.size() - 1) {
            currentIndex++;
        }
    }

    @Override
    public void addTo(Buildable parent) {
        super.addTo(parent);
        if (parent instanceof Map) {
            Map map = (Map) parent;
            map.getBoard().add(diceImageLabel, BorderLayout.PAGE_START);
            map.getBoard().revalidate();
            map.getBoard().repaint();
        }
    }

    @Override
    public void removeFrom(Buildable parent) {
        super.removeFrom(parent);
        if (parent instanceof Map) {
            Map map = (Map) parent;
            map.getBoard().remove(diceImageLabel);
            map.getBoard().revalidate();
            map.getBoard().repaint();
        }
    }
    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        if ("pressed".equals(name)) {
            boolean pressed = Boolean.parseBoolean(value.toString());
            if (pressed) {
                startAnimation();
            } else {
                stopAnimation();
            }
        }
    }

    private void startAnimation() {
        if (diceImages.isEmpty()) {
            return;
        }

        currentIndex = 0;
        timer.start();
    }

    private void stopAnimation() {
        timer.stop();
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{"pressed"};
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{Boolean.class};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{"Pressed"};
    }

    @Override
    public void build(Element element) {
        super.build(element);
        PromptProperty.buildBoolProp(this, Boolean.class, "pressed");
    }

    @Override
    public String[] getAllowableConfigureComponents() {
        return new String[]{Configurer.BUTTON_BAR, Configurer.TOOLBAR_BUTTON};
    }

    @Override
    public Class<? extends Configurer> getConfigurerClass() {
        return StringConfigurer.class;
    }

    @Override
    public HelpFile getHelpFile() {
        return HelpFile.builder("animated-dice")
                .description("Animated Dice")
                .url("https://example.com/animated-dice")
                .build();
    }

    @Override
    public Class<? extends Command>[] getAllowedCommands() {
        return new Class[]{DiceAnimationCommand.class};
    }

    public static class DiceAnimationCommand extends Command {
        private static final long serialVersionUID = 1L;

        public DiceAnimationCommand() {
            this(null);
        }

        public DiceAnimationCommand(CommandEncoder commandEncoder) {
            super(commandEncoder);
        }

        @Override
        public void execute() {
            // No-op, since the animation is handled within the AnimatedDice class
        }

        @Override
        public String toString() {
            return "Dice Animation Command";
        }
    }

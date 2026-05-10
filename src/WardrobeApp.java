import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

// Gives us the start method by saying extend
public class WardrobeApp extends Application {

    // Save folder path constants
    private static final String WARDROBE_PATH =
            "/Users/devendharni/IdeaProjects/WardrobeGenerator/wardrobe/";
    private static final String RESOURCES_PATH =
            "/Users/devendharni/IdeaProjects/WardrobeGenerator/resources/";

    // Save color constants for the hanger graphics
    private static final String WOOD_LIGHT = "#8B5E3C";
    private static final String CREAM = "#F5E6D3";
    private static final String CARD_BG = "#FFFFFF";

    // Set to true to force re-analysis of all items, false to use cached JSON
    private static final boolean FORCE_REANALYZE = false;

    // Store all clothing items by ID and create JSON reader
    private Map<String, JsonNode> itemMap = new HashMap<>();
    private ObjectMapper mapper = new ObjectMapper();

    // Store the outfits section so we can update it from the background thread
    private VBox outfitsSection;

    // GUI entry point here
    @Override
    public void start(Stage stage) throws Exception {
        // Call our helper function to read JSON files
        loadItems();

        // Create the root container
        VBox root = new VBox();

        // Load wood texture as background image and set it to the background
        BackgroundImage woodBg = new BackgroundImage(
                new Image(new FileInputStream(RESOURCES_PATH + "wood.png")),
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, false, true)
        );
        root.setBackground(new Background(woodBg));

        // Build the header in its own method
        VBox header = buildHeader();
        root.getChildren().add(header);

        // 16 is the gap in pixels between each child element and has basic padding
        outfitsSection = new VBox(16);
        outfitsSection.setPadding(new Insets(16, 20, 24, 20));

        // Show a loading message while the pipeline runs in the background
        Label loadingLabel = new Label("Analyzing your wardrobe...");
        loadingLabel.setFont(Font.font("System", FontWeight.MEDIUM, 16));
        loadingLabel.setTextFill(Color.web(CREAM));
        loadingLabel.setPadding(new Insets(40, 0, 0, 0));
        outfitsSection.setAlignment(Pos.TOP_CENTER);
        outfitsSection.getChildren().add(loadingLabel);

        // Wraps the outfit section to make it scrollable
        ScrollPane scroll = new ScrollPane(outfitsSection);
        scroll.setFitToWidth(true);

        // Lets the wood show through
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Add the scroll pane to the root
        root.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Set the dimensions
        Scene scene = new Scene(root, 800, 700);
        stage.setTitle("Wardrobe Generator");
        stage.setScene(scene);
        stage.show();

        // Run the AI pipeline on a background thread so the window stays responsive
        runPipelineInBackground();
    }

    // Runs the full AI pipeline on a separate thread so the UI doesn't freeze
    private void runPipelineInBackground() {
        // Thread is like a separate worker that runs code independently from the main UI thread
        Thread pipelineThread = new Thread(() -> {
            try {
                // Create the pipeline objects
                ClothingAnalyzer analyzer = new ClothingAnalyzer();
                CompatibilityScorer scorer = new CompatibilityScorer();
                OutfitGenerator generator = new OutfitGenerator(scorer);

                // Scan the wardrobe folder for all image files
                File wardrobeFolder = new File(WARDROBE_PATH);
                File[] imageFiles = wardrobeFolder.listFiles((dir, name) ->
                        name.toLowerCase().endsWith(".jpg") ||
                                name.toLowerCase().endsWith(".jpeg") ||
                                name.toLowerCase().endsWith(".png") &&
                                        !name.endsWith(".json"));

                if (imageFiles == null || imageFiles.length == 0) {
                    // Platform.runLater schedules this UI update on the JavaFX thread
                    Platform.runLater(() -> showError("No images found in wardrobe folder."));
                    return;
                }

                // Analyze each image and build the wardrobe list
                List<ClothingItem> wardrobe = new ArrayList<>();
                for (int i = 0; i < imageFiles.length; i++) {
                    final int current = i + 1;
                    final int total = imageFiles.length;
                    final String imageName = imageFiles[i].getName();

                    String itemId = "item_" + String.format("%03d", i + 1);
                    String jsonPath = WARDROBE_PATH + itemId + ".json";
                    File jsonFile = new File(jsonPath);

                    ClothingItem item;

                    // Skip analysis if JSON already exists — saves API costs
                    if (jsonFile.exists() && !FORCE_REANALYZE) {
                        Platform.runLater(() -> updateLoadingMessage(
                                "Loading " + current + " of " + total + ": " + imageName));
                        item = loadItemFromJson(jsonFile, itemId, imageFiles[i].getAbsolutePath());
                    } else {
                        Platform.runLater(() -> updateLoadingMessage(
                                "Analyzing " + current + " of " + total + ": " + imageName));
                        item = analyzer.analyze(imageFiles[i].getAbsolutePath(), itemId);
                        analyzer.saveToJson(item, jsonPath);
                    }

                    wardrobe.add(item);
                }

                // Reload itemMap now that all JSON files are saved
                Platform.runLater(() -> {
                    try {
                        loadItems();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                // Update loading message while generating outfits
                Platform.runLater(() -> updateLoadingMessage("Generating outfits..."));

                // Run the graph traversal to find top outfits
                List<OutfitGenerator.Outfit> topOutfits = generator.generateTopOutfits(wardrobe);

                // Hand the results back to the JavaFX thread to update the UI
                Platform.runLater(() -> displayOutfits(topOutfits));

            } catch (Exception e) {
                // If anything goes wrong show an error message on screen
                Platform.runLater(() -> showError("Pipeline error: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        // Mark as daemon so it shuts down when the window closes
        pipelineThread.setDaemon(true);
        pipelineThread.start();
    }

    // Loads a ClothingItem from an existing JSON file instead of re-running the pipeline
    private ClothingItem loadItemFromJson(File jsonFile, String itemId, String imagePath) {
        try {
            JsonNode node = mapper.readTree(jsonFile);
            ClothingItem item = new ClothingItem(itemId, imagePath);

            // Parse type
            String type = node.get("type").asText();
            item.setType(parseType(type));

            // Parse subtype, material, fit
            item.setSubType(node.get("subType").asText());
            item.setMaterial(node.get("material").asText());
            item.setFit(node.get("fit").asText());

            // Parse pattern
            String pattern = node.get("pattern").asText();
            item.setPattern(parsePattern(pattern));

            // Parse formality
            String formality = node.get("formality").asText();
            item.setFormality(parseFormality(formality));

            // Parse colors
            item.setPrimaryColor(node.get("primaryColor").asText());
            item.setColorFamily(node.get("colorFamily").asText());

            // Parse accent colors list
            List<String> accents = new ArrayList<>();
            if (node.get("accentColors") != null) {
                for (JsonNode accent : node.get("accentColors")) {
                    accents.add(accent.asText());
                }
            }
            item.setAccentColors(accents);

            // Parse styles list
            List<String> styles = new ArrayList<>();
            if (node.get("styles") != null) {
                for (JsonNode style : node.get("styles")) {
                    styles.add(style.asText());
                }
            }
            item.setStyles(styles);

            // Parse occasions list
            List<String> occasions = new ArrayList<>();
            if (node.get("suitableOccasions") != null) {
                for (JsonNode occ : node.get("suitableOccasions")) {
                    occasions.add(occ.asText());
                }
            }
            item.setSuitableOccasions(occasions);

            return item;
        } catch (Exception e) {
            System.out.println("Error loading JSON for " + itemId + ": " + e.getMessage());
            return new ClothingItem(itemId, imagePath);
        }
    }

    // Function that parses through the type of clothing and returns the matching enum
    private ClothingType parseType(String raw) {
        // Convert to lowercase and remove whitespace before comparing
        switch (raw.toLowerCase().trim()) {
            // Check the string against each case and return the matching enum value
            case "top": return ClothingType.TOP;
            case "bottom": return ClothingType.BOTTOM;
            case "dress": return ClothingType.DRESS;
            case "outerwear": return ClothingType.OUTERWEAR;
            case "shoes": return ClothingType.SHOES;
            case "accessory": return ClothingType.ACCESSORY;

            // If nothing matches, return top as a safe case
            default: return ClothingType.TOP;
        }
    }

    // Same pattern as parseType but with the formality
    private Formality parseFormality(String raw) {
        switch (raw.toLowerCase().trim()) {
            case "casual everyday": return Formality.CASUAL_EVERYDAY;
            case "smart casual": return Formality.SMART_CASUAL;
            case "business casual": return Formality.BUSINESS_CASUAL;
            case "formal": return Formality.FORMAL;
            case "athleisure sportswear": return Formality.ATHLEISURE_SPORTSWEAR;
            default: return Formality.CASUAL_EVERYDAY;
        }
    }

    // Same pattern as parseType but with the pattern
    private Pattern parsePattern(String raw) {
        switch (raw.toLowerCase().trim()) {
            case "solid": return Pattern.SOLID;
            case "striped": return Pattern.STRIPED;
            case "plaid": return Pattern.PLAID;
            case "floral": return Pattern.FLORAL;
            case "checkered": return Pattern.CHECKERED;
            case "graphic print": return Pattern.GRAPHIC_PRINT;
            case "abstract": return Pattern.ABSTRACT;
            default: return Pattern.SOLID;
        }
    }

    // Updates the loading message text — called from the background thread via Platform.runLater
    private void updateLoadingMessage(String message) {
        outfitsSection.getChildren().clear();
        Label loadingLabel = new Label(message);
        loadingLabel.setFont(Font.font("System", FontWeight.MEDIUM, 16));
        loadingLabel.setTextFill(Color.web(CREAM));
        loadingLabel.setPadding(new Insets(40, 0, 0, 0));
        outfitsSection.getChildren().add(loadingLabel);
    }

    // Shows an error message on screen
    private void showError(String message) {
        outfitsSection.getChildren().clear();
        Label errorLabel = new Label(message);
        errorLabel.setFont(Font.font("System", 14));
        errorLabel.setTextFill(Color.web("#FF6B6B"));
        errorLabel.setPadding(new Insets(40, 0, 0, 0));
        outfitsSection.getChildren().add(errorLabel);
    }

    // Builds and displays the outfit cards from the pipeline results
    private void displayOutfits(List<OutfitGenerator.Outfit> topOutfits) {
        // Clear the loading message
        outfitsSection.getChildren().clear();
        outfitsSection.setAlignment(Pos.TOP_LEFT);

        if (topOutfits.isEmpty()) {
            showError("No compatible outfits found. Try adding more clothing items.");
            return;
        }

        // Build a card for each outfit using the live pipeline results
        for (int i = 0; i < topOutfits.size(); i++) {
            OutfitGenerator.Outfit outfit = topOutfits.get(i);

            // Extract item IDs from the outfit
            List<String> ids = new ArrayList<>();
            for (ClothingItem item : outfit.items) {
                ids.add(item.getId());
            }

            VBox outfitCard = buildOutfitCard(
                    ids.toArray(new String[0]),
                    outfit.score,
                    i
            );
            outfitsSection.getChildren().add(outfitCard);
        }
    }

    // Same scanning file pattern as in ClothingAnalyzer here
    // Reads all of the JSON files
    private void loadItems() throws Exception {
        File folder = new File(WARDROBE_PATH);
        File[] jsonFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) return;
        itemMap.clear();
        for (File f : jsonFiles) {
            JsonNode node = mapper.readTree(f);
            String id = node.get("id").asText();
            itemMap.put(id, node);
        }
    }

    // Function to build each header
    private VBox buildHeader() {
        // 4px gap between each child
        VBox header = new VBox(4);

        // Centers all the children and let wood texture show through
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24, 20, 12, 20));
        header.setStyle("-fx-background-color: transparent;");

        // 24pxs wide, 10pxs tall
        Rectangle hookTop = new Rectangle(24, 10);
        hookTop.setArcWidth(12);
        hookTop.setArcHeight(12);
        hookTop.setFill(Color.web(WOOD_LIGHT));

        // The vertical part of the hanger
        Rectangle stem = new Rectangle(3, 18);
        stem.setFill(Color.web(WOOD_LIGHT));

        // The horizontal bar
        Rectangle bar = new Rectangle(300, 4);
        bar.setArcWidth(4);
        bar.setArcHeight(4);
        bar.setFill(Color.web(WOOD_LIGHT));

        // The main text
        Label title = new Label("Your Wardrobe");
        title.setFont(Font.font("System", FontWeight.MEDIUM, 26));
        title.setTextFill(Color.web(CREAM));
        title.setPadding(new Insets(10, 0, 0, 0));

        Label subtitle = new Label("AI-generated outfit combinations");
        subtitle.setFont(Font.font("System", 13));
        subtitle.setTextFill(Color.web("#F5E6D3AA"));

        // Add all the children and return
        header.getChildren().addAll(hookTop, stem, bar, title, subtitle);
        return header;
    }

    // Function to build each outfit
    private VBox buildOutfitCard(String[] itemIds, double score, int index) {
        // Creates a white rounded card for one outfit
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle(
                "-fx-background-color: " + CARD_BG + ";" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-radius: 16;"
        );

        // Arranges the children left to right
        HBox cardHeader = new HBox();
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        // If the index is within labels array use that label, if not go basic like "outfit 1"
        String[] labels = {"Best match", "Runner up", "Third pick"};
        Label titleLabel = new Label(index < labels.length ? labels[index] : "Outfit " + (index + 1));
        titleLabel.setFont(Font.font("System", FontWeight.MEDIUM, 15));
        titleLabel.setTextFill(Color.web("#1a1a1a"));

        // An invisible element that just takes up some space
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Formats the score to one decimal point
        Label scoreLabel = new Label(String.format("%.1f / 100", score));
        scoreLabel.setStyle(
                "-fx-background-color: #5C3317;" +
                        "-fx-background-radius: 20;" +
                        "-fx-text-fill: " + CREAM + ";" +
                        "-fx-font-size: 12;" +
                        "-fx-padding: 4 12 4 12;"
        );

        cardHeader.getChildren().addAll(titleLabel, spacer, scoreLabel);

        HBox itemsRow = new HBox(10);
        itemsRow.setAlignment(Pos.CENTER_LEFT);

        // Sorts the outfits in body order
        String[] typeOrder = {"TOP", "BOTTOM", "OUTERWEAR", "SHOES", "ACCESSORY"};

        // A hashmap that remembers the insertion order
        Map<String, JsonNode> byType = new LinkedHashMap<>();
        for (String id : itemIds) {
            JsonNode item = itemMap.get(id);
            if (item != null) {
                String type = item.get("type").asText();

                // Adds the items in the specific order given
                byType.put(type, item);
            }
        }

        // Goes through the type order and adds the items in that order
        List<JsonNode> sortedItems = new ArrayList<>();
        for (String type : typeOrder) {
            if (byType.containsKey(type)) {
                sortedItems.add(byType.get(type));
            }
        }

        for (JsonNode item : sortedItems) {
            VBox itemCard = buildItemCard(item);
            itemsRow.getChildren().add(itemCard);
        }

        card.getChildren().addAll(cardHeader, itemsRow);
        return card;
    }

    // Function that builds each item
    private VBox buildItemCard(JsonNode item) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(160);
        card.setMinHeight(270);
        card.setStyle(
                "-fx-background-color: #F7F7F7;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;"
        );

        // Get the image stored in JSON
        String imagePath = item.get("imagePath").asText();
        ImageView imageView = new ImageView();

        // Set the display size
        imageView.setFitWidth(160);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(false);

        // Make sure the file exists before opening it
        try {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Image img = new Image(new FileInputStream(imgFile));
                imageView.setImage(img);
            }
        } catch (Exception e) {
            // Image not found — show empty card
        }

        // Clips the visible area to a rectangle
        Rectangle clip = new Rectangle(160, 200);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imageView.setClip(clip);

        // Create small vertical box below the image for text info
        VBox info = new VBox(2);
        info.setPadding(new Insets(8, 10, 10, 10));
        info.setStyle("-fx-background-color: #F7F7F7;");

        // Gets the type from JSON and displays it
        String type = item.get("type").asText();
        Label typeLabel = new Label(type);
        typeLabel.setFont(Font.font("System", 10));
        typeLabel.setStyle("-fx-text-fill: black;");

        // Gets the subtype from JSON and displays it
        String subType = item.get("subType").asText();
        Label subTypeLabel = new Label(subType);
        subTypeLabel.setFont(Font.font("System", FontWeight.MEDIUM, 13));
        subTypeLabel.setStyle("-fx-text-fill: black;");

        // Gets the color from JSON and displays it
        String primaryColor = item.get("primaryColor").asText();
        HBox colorRow = new HBox(5);
        colorRow.setAlignment(Pos.CENTER_LEFT);

        // Creates a small color swatch next to the color name
        Rectangle colorDot = new Rectangle(8, 8);
        colorDot.setArcWidth(8);
        colorDot.setArcHeight(8);
        colorDot.setFill(Color.web(getColorHex(primaryColor)));

        Label colorLabel = new Label(primaryColor);
        colorLabel.setFont(Font.font("System", 11));
        colorLabel.setStyle("-fx-text-fill: black;");

        // Assembles the info section and the full card together
        colorRow.getChildren().addAll(colorDot, colorLabel);
        info.getChildren().addAll(typeLabel, subTypeLabel, colorRow);
        card.getChildren().addAll(imageView, info);
        System.out.println("Type: " + type + " | SubType: " + subType + " | Color: " + primaryColor);
        return card;
    }

    // Lookup table mapping colors to hex values
    private String getColorHex(String colorName) {
        Map<String, String> colors = new HashMap<>();
        colors.put("black", "#1a1a1a");
        colors.put("white", "#f8f8f8");
        colors.put("gray", "#9e9e9e");
        colors.put("light gray", "#d3d3d3");
        colors.put("dark gray", "#555555");
        colors.put("charcoal", "#36454f");
        colors.put("navy", "#001f5b");
        colors.put("dark navy", "#0a1628");
        colors.put("blue", "#1565c0");
        colors.put("light blue", "#90caf9");
        colors.put("red", "#c62828");
        colors.put("burgundy", "#6d0020");
        colors.put("pink", "#f48fb1");
        colors.put("orange", "#e65100");
        colors.put("coral", "#ff7043");
        colors.put("yellow", "#f9a825");
        colors.put("mustard", "#f57f17");
        colors.put("gold", "#ffc107");
        colors.put("green", "#2e7d32");
        colors.put("olive", "#827717");
        colors.put("sage", "#b2c5a2");
        colors.put("mint", "#a5d6a7");
        colors.put("teal", "#00695c");
        colors.put("purple", "#6a1b9a");
        colors.put("lavender", "#ce93d8");
        colors.put("plum", "#6a0572");
        colors.put("brown", "#4e342e");
        colors.put("tan", "#d2b48c");
        colors.put("rust", "#b7410e");
        colors.put("beige", "#f5f5dc");
        colors.put("cream", "#fffdd0");
        return colors.getOrDefault(colorName != null ? colorName.toLowerCase() : "", "#bbbbbb");
    }

    // Actual entry point when you click run
    public static void main(String[] args) {
        launch(args);
    }
}
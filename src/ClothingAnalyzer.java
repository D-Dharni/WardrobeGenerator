// Jackson imports
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Import everything from java's input/output package
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ClothingAnalyzer {
    // Save the path to my python and the path to the scripts
    private static final String PYTHON_PATH =
            "/Users/devendharni/IdeaProjects/WardrobeGenerator/venv/bin/python3";
    private static final String SCRIPTS_PATH =
            "/Users/devendharni/IdeaProjects/WardrobeGenerator/python/";

    // Create an object mapper that the whole class will use
    private final ObjectMapper mapper = new ObjectMapper();

    // Main function to return a fully assembled ClothingItem
    public ClothingItem analyze(String imagePath, String itemId) throws Exception {
        // Station 1 - Crop
        runCrop(imagePath);
        String croppedPath = SCRIPTS_PATH + "cropped.jpg";

        // Station 2 - Classify
        String classifyJson = runScript("classify.py", croppedPath);
        JsonNode classifyResult = mapper.readTree(classifyJson);

        // Station 3 - Context
        String contextJson = runScript("context.py", croppedPath);
        JsonNode contextResult = mapper.readTree(contextJson);

        // Station 4 - Colors
        String colorsJson = runScript("colors.py", croppedPath);
        JsonNode colorsResult = mapper.readTree(colorsJson);

        // Station 5 - Assemble ClothingItem
        ClothingItem item = new ClothingItem(itemId, imagePath);

        // From classify.py
        item.setType(parseType(classifyResult.get("type").asText()));
        item.setSubType(classifyResult.get("subType").asText());
        item.setPattern(parsePattern(classifyResult.get("pattern").asText()));
        item.setMaterial(classifyResult.get("material").asText());
        item.setFit(classifyResult.get("fit").asText());

        // From context.py
        item.setFormality(parseFormality(contextResult.get("formality").asText()));
        item.setStyles(jsonArrayToList(contextResult.get("styles")));
        item.setSuitableOccasions(jsonArrayToList(contextResult.get("suitableOccasions")));

        // From colors.py
        item.setPrimaryColor(colorsResult.get("primaryColor").asText());
        item.setAccentColors(jsonArrayToList(colorsResult.get("accentColors")));
        item.setColorFamily(colorsResult.get("colorFamily").asText());

        // Return the final item
        return item;
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

    // Returns a string of output from the python code
    private String readOutput(Process process) throws Exception {
        // Get output stream of the python process to read it one line at a time
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        // Using a more efficient way to build a string piece by piece than a "+"
        StringBuilder sb = new StringBuilder();
        String line;

        // Read in one line at a time
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }

        // Convert builder to plain string and return
        return sb.toString();
    }

    // Helper function to run the crop function
    private void runCrop(String imagePath) throws Exception {
        // Java's built-in class for running external programs
        ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, SCRIPTS_PATH + "crop.py", imagePath);

        // Set the working directory for the process
        pb.directory(new File(SCRIPTS_PATH));

        // Merge the output error to capture everything in one go
        pb.redirectErrorStream(true);

        // Launch the external process, read and discard output, wait until script is done
        Process process = pb.start();
        readOutput(process);
        process.waitFor();
    }

    // Helper function to run the classify, context, and color scripts
    private String runScript(String scriptName, String imagePath) throws Exception {
        // Same set up as run crop here
        ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, SCRIPTS_PATH + scriptName, imagePath
        );
        pb.directory(new File(SCRIPTS_PATH));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Actually read the output instead of discarding it
        String output = readOutput(process);
        process.waitFor();

        // Loop through each line and only if it is actually a JSON object
        for (String line : output.split("\n")) {
            if (line.trim().startsWith("{")) {
                return line.trim();
            }
        }

        // If it doesn't work throw an exception
        throw new RuntimeException("No JSON found in output of " + scriptName);
    }

    // Converts a JsonNode to an array list
    private List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();

        // Add each element into new list as a string
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                list.add(node.asText());
            }
        }

        // Return final list
        return list;
    }

    // Function to permanently save clothing object as a JSON to hard drive
    public void saveToJson(ClothingItem item, String outputPath) throws Exception {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), item);
    }

    public static void main(String[] args) throws Exception {
        // Create the objects that we will need for the analysis
        ClothingAnalyzer analyzer = new ClothingAnalyzer();
        CompatibilityScorer scorer = new CompatibilityScorer();

        // Scan the wardrobe folder for all jpg/png images
        File wardrobeFolder = new File(
                "/Users/devendharni/IdeaProjects/WardrobeGenerator/wardrobe/");
        File[] imageFiles = wardrobeFolder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jpg") ||
                        name.toLowerCase().endsWith(".jpeg") ||
                        name.toLowerCase().endsWith(".png"));

        // Return null if folder is empty
        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("No images found in wardrobe folder!");
            return;
        }

        // Analyze each image
        List<ClothingItem> wardrobe = new ArrayList<>();
        for (int i = 0; i < imageFiles.length; i++) {
            // Access the object at the index and run the analyzer
            System.out.println("Analyzing " + imageFiles[i].getName() + "...");
            ClothingItem item = analyzer.analyze(
                    // Generates a unique id
                    imageFiles[i].getAbsolutePath(),
                    "item_" + String.format("%03d", i + 1)
            );

            // Add to the wardrobe list
            wardrobe.add(item);

            // Save the file to hard drive for long-term storage
            analyzer.saveToJson(item,
                    "/Users/devendharni/IdeaProjects/WardrobeGenerator/wardrobe/" +
                            "item_" + String.format("%03d", i + 1) + ".json");
            System.out.println("Done: " + item.getType() + " - " + item.getPrimaryColor());
        }

        System.out.println("\nAnalyzed " + wardrobe.size() + " items.");
        System.out.println("Ready for graph traversal.");

        // Generate the top outfits
        OutfitGenerator generator = new OutfitGenerator(scorer);
        List<OutfitGenerator.Outfit> topOutfits = generator.generateTopOutfits(wardrobe);

        // Print out each of the top outfits
        System.out.println("\n=== TOP OUTFITS ===");
        for (int i = 0; i < topOutfits.size(); i++) {
            OutfitGenerator.Outfit outfit = topOutfits.get(i);
            System.out.println("\nOutfit " + (i + 1) + " — Score: " + outfit.score);
            for (ClothingItem item : outfit.items) {
                System.out.println("  " + item.getId() + " | " + item.getType() + " | " + item.getPrimaryColor() + " | " + item.getSubType());
            }
        }
    }
}
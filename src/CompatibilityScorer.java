import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompatibilityScorer {

    // Current weight for each category
    private static final double W_TYPE       = 0.15;
    private static final double W_FORMALITY  = 0.20;
    private static final double W_COLOR      = 0.20;
    private static final double W_PATTERN    = 0.10;
    private static final double W_STYLE      = 0.15;
    private static final double W_MATERIAL   = 0.05;
    private static final double W_FIT        = 0.05;
    private static final double W_OCCASION   = 0.10;

    // Lookup table declarations

    // 2D Matrix for Maps of Maps
    private final Map<ClothingType, Map<ClothingType, Double>> typeMatrix;
    private final Map<Pattern, Map<Pattern, Double>> patternMatrix;
    private final Map<String, Map<String, Double>> fitMatrix;
    private final Map<String, Map<String, Double>> materialMatrix;
    private final Map<String, Double> hueMap;

    // Constructor to build all five lookup tables through helper method
    public CompatibilityScorer() {
        typeMatrix    = buildTypeMatrix();
        patternMatrix = buildPatternMatrix();
        fitMatrix     = buildFitMatrix();
        materialMatrix = buildMaterialMatrix();
        hueMap        = buildHueMap();
    }

    // Main scoring method
    public double score(ClothingItem a, ClothingItem b) {
        // Calculate the total score by calling helper functions and multiplying by weights
        double total =
                W_TYPE      * scoreType(a, b)      +
                        W_FORMALITY * scoreFormality(a, b) +
                        W_COLOR     * scoreColor(a, b)     +
                        W_PATTERN   * scorePattern(a, b)   +
                        W_STYLE     * scoreStyle(a, b)     +
                        W_MATERIAL  * scoreMaterial(a, b)  +
                        W_FIT       * scoreFit(a, b)       +
                        W_OCCASION  * scoreOccasion(a, b);

        return Math.round(total * 100.0 * 10.0) / 10.0;
    }

    // Function that returns the score for the clothing type compatibility
    private double scoreType(ClothingItem a, ClothingItem b) {
        // Get the types from both clothing items
        ClothingType t1 = a.getType();
        ClothingType t2 = b.getType();

        // Have a protective check
        if (t1 == null || t2 == null) return 0.5;

        // Get the row in our matrix
        Map<ClothingType, Double> row = typeMatrix.get(t1);
        if (row == null) return 0.5;

        // Lookup the next item in the row to get the correct row/column value
        return row.getOrDefault(t2, 0.5);
    }

    // Function that returns the score for the clothing formality compatibility
    private double scoreFormality(ClothingItem a, ClothingItem b) {
        // Get the formalities from both clothing items
        int level1 = formalityLevel(a.getFormality());
        int level2 = formalityLevel(b.getFormality());

        // Get the absolute value difference between two levels
        int distance = Math.abs(level1 - level2);
        return 1.0 - (distance / 4.0);
    }

    // Function that maps each formality enum to a set value
    private int formalityLevel(Formality f) {
        if (f == null) return 2;
        switch (f) {
            case ATHLEISURE_SPORTSWEAR: return 1;
            case CASUAL_EVERYDAY:       return 2;
            case SMART_CASUAL:          return 3;
            case BUSINESS_CASUAL:       return 4;
            case FORMAL:                return 5;
            default:                    return 2;
        }
    }

    // Function that returns the score for the clothing color compatibility
    private double scoreColor(ClothingItem a, ClothingItem b) {
        // Get all of the color related data
        String primary1 = a.getPrimaryColor();
        String primary2 = b.getPrimaryColor();
        List<String> accents1 = a.getAccentColors();
        List<String> accents2 = b.getAccentColors();
        String family1 = a.getColorFamily();
        String family2 = b.getColorFamily();

        // Make sure that primary scores are there
        if (primary1 == null || primary2 == null) return 0.5;

        // Call helper that uses color wheel math
        double hueScore = getHueScore(primary1, primary2);

        // Neutral colors get bonus points
        double neutralBonus = 0.0;
        if (isNeutral(primary1) || isNeutral(primary2)) {
            neutralBonus = 0.10;
        }

        // Similar color family gives a bonus
        double familyBonus = 0.0;
        if (family1 != null && family1.equals(family2)) {
            familyBonus = 0.05;
        }

        // If primary = accent in one and accent = primary in other give a bonus
        double accentBonus = 0.0;
        if (accents1 != null && accents1.contains(primary2)) {
            accentBonus = 0.10;
        } else if (accents2 != null && accents2.contains(primary1)) {
            accentBonus = 0.10;
        }

        // Cap the result at 1.0
        return Math.min(1.0, hueScore + neutralBonus + familyBonus + accentBonus);
    }

    // Helper function that calculates hue score using color wheel
    private double getHueScore(String color1, String color2) {
        // If either color is neutral just return a flat .75
        if (isNeutral(color1) || isNeutral(color2)) return 0.75;

        // Lookup each color's hue degree
        Double hue1 = hueMap.get(color1.toLowerCase().trim());
        Double hue2 = hueMap.get(color2.toLowerCase().trim());

        // If the color isn't in the map, return a below-average neutral score
        if (hue1 == null || hue2 == null) return 0.60;

        // Calculate the circular difference on the color wheel
        double diff = Math.abs(hue1 - hue2);
        if (diff > 180) diff = 360 - diff;

        if (diff <= 30)                  return 0.85; // analogous (colors next to each other)
        else if (diff <= 60)             return 0.75; // near analogous (still harmonious)
        else if (diff <= 90)             return 0.80; // split complementary (one step off)
        else if (diff <= 150)            return 0.65; // tension
        else if (diff <= 210)            return 0.90; // complementary
        else                             return 0.55; // clashing (colors that fight each other)
    }

    // A helper function that is just a lookup method to see if the color is neutral
    private boolean isNeutral(String color) {
        if (color == null) return false;
        switch (color.toLowerCase().trim()) {
            case "white": case "off white": case "cream": case "ivory":
            case "beige": case "linen": case "light gray": case "silver":
            case "gray": case "medium gray": case "dark gray": case "charcoal":
            case "black": case "off black": case "tan": case "brown":
            case "light brown": case "dark brown": case "sand": case "khaki":
                return true;
            default:
                return false;
        }
    }

    // Function that returns the score for the clothing pattern compatibility
    private double scorePattern(ClothingItem a, ClothingItem b) {
        // Get the pattern values
        Pattern p1 = a.getPattern();
        Pattern p2 = b.getPattern();

        // Safeguard
        if (p1 == null || p2 == null) return 0.5;

        // Get the row and column and return
        Map<Pattern, Double> row = patternMatrix.get(p1);
        if (row == null) return 0.5;
        return row.getOrDefault(p2, 0.5);
    }

    // Function that returns the score for the clothing style compatibility
    private double scoreStyle(ClothingItem a, ClothingItem b) {
        // Get the style tags and safeguard
        List<String> styles1 = a.getStyles();
        List<String> styles2 = b.getStyles();
        if (styles1 == null || styles2 == null ||
                styles1.isEmpty() || styles2.isEmpty()) return 0.5;

        // Convert both lists to hash sets for fast operations and removing duplicates
        Set<String> set1 = new HashSet<>(styles1);
        Set<String> set2 = new HashSet<>(styles2);

        // Keep only elements that are in both sets
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Combine the elements both sets into one set
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Formula to measure how similar two sets are
        double jaccard = (double) intersection.size() / union.size();

        // If two or more styles fit add a boost
        if (intersection.size() >= 2) jaccard = Math.min(1.0, jaccard + 0.15);

        // If no styles overlap at all, return neutral (0.5) instead of 0.0
        // so a style mismatch doesn't unfairly destroy the total score
        if (jaccard == 0.0) return 0.5;

        return jaccard;
    }

    // Function that returns the score for the clothing material compatibility
    private double scoreMaterial(ClothingItem a, ClothingItem b) {
        // Get the materials
        String m1 = a.getMaterial();
        String m2 = b.getMaterial();

        // Safeguard
        if (m1 == null || m2 == null) return 0.5;

        // Before doing a matrix lookup just check if materials are the same
        if (m1.equalsIgnoreCase(m2)) return 0.85;

        // Lookup the row and column and return
        Map<String, Double> row = materialMatrix.get(m1.toLowerCase().trim());
        if (row == null) return 0.60;
        return row.getOrDefault(m2.toLowerCase().trim(), 0.60);
    }

    // Function that returns the score for the clothing fit compatibility
    private double scoreFit(ClothingItem a, ClothingItem b) {
        // Get the fit values
        String f1 = a.getFit();
        String f2 = b.getFit();

        // Safeguard
        if (f1 == null || f2 == null) return 0.5;

        // Get row and column and return
        Map<String, Double> row = fitMatrix.get(f1.toLowerCase().trim());
        if (row == null) return 0.5;
        return row.getOrDefault(f2.toLowerCase().trim(), 0.5);
    }

    // Function that returns the score for the clothing occasion compatibility
    private double scoreOccasion(ClothingItem a, ClothingItem b) {
        // Get the values and safeguard
        List<String> occ1 = a.getSuitableOccasions();
        List<String> occ2 = b.getSuitableOccasions();
        if (occ1 == null || occ2 == null ||
                occ1.isEmpty() || occ2.isEmpty()) return 0.5;

        // Convert both lists to hash sets for fast operations and removing duplicates
        Set<String> set1 = new HashSet<>(occ1);
        Set<String> set2 = new HashSet<>(occ2);

        // Keep only elements that are in both sets
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Combine the set together
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    // =====================
    // BUILD LOOKUP TABLES
    // =====================

    private Map<ClothingType, Map<ClothingType, Double>> buildTypeMatrix() {
        Map<ClothingType, Map<ClothingType, Double>> m = new HashMap<>();

        m.put(ClothingType.TOP, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       0.10);
            put(ClothingType.BOTTOM,    1.00);
            put(ClothingType.DRESS,     0.20);
            put(ClothingType.OUTERWEAR, 0.90);
            put(ClothingType.SHOES,     0.90);
            put(ClothingType.ACCESSORY, 0.85);
        }});

        m.put(ClothingType.BOTTOM, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       1.00);
            put(ClothingType.BOTTOM,    0.10);
            put(ClothingType.DRESS,     0.15);
            put(ClothingType.OUTERWEAR, 0.85);
            put(ClothingType.SHOES,     0.95);
            put(ClothingType.ACCESSORY, 0.80);
        }});

        m.put(ClothingType.DRESS, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       0.20);
            put(ClothingType.BOTTOM,    0.15);
            put(ClothingType.DRESS,     0.10);
            put(ClothingType.OUTERWEAR, 0.85);
            put(ClothingType.SHOES,     0.95);
            put(ClothingType.ACCESSORY, 0.90);
        }});

        m.put(ClothingType.OUTERWEAR, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       0.90);
            put(ClothingType.BOTTOM,    0.85);
            put(ClothingType.DRESS,     0.85);
            put(ClothingType.OUTERWEAR, 0.10);
            put(ClothingType.SHOES,     0.85);
            put(ClothingType.ACCESSORY, 0.80);
        }});

        m.put(ClothingType.SHOES, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       0.90);
            put(ClothingType.BOTTOM,    0.95);
            put(ClothingType.DRESS,     0.95);
            put(ClothingType.OUTERWEAR, 0.85);
            put(ClothingType.SHOES,     0.05);
            put(ClothingType.ACCESSORY, 0.75);
        }});

        m.put(ClothingType.ACCESSORY, new HashMap<ClothingType, Double>() {{
            put(ClothingType.TOP,       0.85);
            put(ClothingType.BOTTOM,    0.80);
            put(ClothingType.DRESS,     0.90);
            put(ClothingType.OUTERWEAR, 0.80);
            put(ClothingType.SHOES,     0.75);
            put(ClothingType.ACCESSORY, 0.70);
        }});

        return m;
    }

    private Map<Pattern, Map<Pattern, Double>> buildPatternMatrix() {
        Map<Pattern, Map<Pattern, Double>> m = new HashMap<>();

        m.put(Pattern.SOLID, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.90);
            put(Pattern.STRIPED,       0.85);
            put(Pattern.PLAID,         0.85);
            put(Pattern.FLORAL,        0.80);
            put(Pattern.CHECKERED,     0.85);
            put(Pattern.GRAPHIC_PRINT, 0.75);
            put(Pattern.ABSTRACT,      0.70);
        }});

        m.put(Pattern.STRIPED, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.85);
            put(Pattern.STRIPED,       0.60);
            put(Pattern.PLAID,         0.35);
            put(Pattern.FLORAL,        0.40);
            put(Pattern.CHECKERED,     0.50);
            put(Pattern.GRAPHIC_PRINT, 0.45);
            put(Pattern.ABSTRACT,      0.50);
        }});

        m.put(Pattern.PLAID, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.85);
            put(Pattern.STRIPED,       0.35);
            put(Pattern.PLAID,         0.30);
            put(Pattern.FLORAL,        0.25);
            put(Pattern.CHECKERED,     0.40);
            put(Pattern.GRAPHIC_PRINT, 0.30);
            put(Pattern.ABSTRACT,      0.35);
        }});

        m.put(Pattern.FLORAL, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.80);
            put(Pattern.STRIPED,       0.40);
            put(Pattern.PLAID,         0.25);
            put(Pattern.FLORAL,        0.35);
            put(Pattern.CHECKERED,     0.30);
            put(Pattern.GRAPHIC_PRINT, 0.40);
            put(Pattern.ABSTRACT,      0.45);
        }});

        m.put(Pattern.CHECKERED, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.85);
            put(Pattern.STRIPED,       0.50);
            put(Pattern.PLAID,         0.40);
            put(Pattern.FLORAL,        0.30);
            put(Pattern.CHECKERED,     0.35);
            put(Pattern.GRAPHIC_PRINT, 0.40);
            put(Pattern.ABSTRACT,      0.40);
        }});

        m.put(Pattern.GRAPHIC_PRINT, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.75);
            put(Pattern.STRIPED,       0.45);
            put(Pattern.PLAID,         0.30);
            put(Pattern.FLORAL,        0.40);
            put(Pattern.CHECKERED,     0.40);
            put(Pattern.GRAPHIC_PRINT, 0.40);
            put(Pattern.ABSTRACT,      0.45);
        }});

        m.put(Pattern.ABSTRACT, new HashMap<Pattern, Double>() {{
            put(Pattern.SOLID,         0.70);
            put(Pattern.STRIPED,       0.50);
            put(Pattern.PLAID,         0.35);
            put(Pattern.FLORAL,        0.45);
            put(Pattern.CHECKERED,     0.40);
            put(Pattern.GRAPHIC_PRINT, 0.45);
            put(Pattern.ABSTRACT,      0.40);
        }});

        return m;
    }

    private Map<String, Map<String, Double>> buildFitMatrix() {
        Map<String, Map<String, Double>> m = new HashMap<>();

        m.put("slim fit", new HashMap<String, Double>() {{
            put("slim fit",     0.80);
            put("regular fit",  0.85);
            put("oversized",    0.70);
            put("relaxed fit",  0.80);
            put("fitted",       0.85);
        }});

        m.put("regular fit", new HashMap<String, Double>() {{
            put("slim fit",     0.85);
            put("regular fit",  0.85);
            put("oversized",    0.70);
            put("relaxed fit",  0.85);
            put("fitted",       0.85);
        }});

        m.put("oversized", new HashMap<String, Double>() {{
            put("slim fit",     0.65);
            put("regular fit",  0.70);
            put("oversized",    0.40);
            put("relaxed fit",  0.60);
            put("fitted",       0.70);
        }});

        m.put("relaxed fit", new HashMap<String, Double>() {{
            put("slim fit",     0.80);
            put("regular fit",  0.85);
            put("oversized",    0.60);
            put("relaxed fit",  0.75);
            put("fitted",       0.80);
        }});

        m.put("fitted", new HashMap<String, Double>() {{
            put("slim fit",     0.85);
            put("regular fit",  0.85);
            put("oversized",    0.70);
            put("relaxed fit",  0.80);
            put("fitted",       0.80);
        }});

        return m;
    }

    private Map<String, Map<String, Double>> buildMaterialMatrix() {
        Map<String, Map<String, Double>> m = new HashMap<>();

        m.put("cotton", new HashMap<String, Double>() {{
            put("cotton",     0.85);
            put("denim",      0.90);
            put("wool",       0.80);
            put("polyester",  0.75);
            put("linen",      0.85);
            put("leather",    0.70);
            put("silk",       0.65);
            put("synthetic",  0.70);
        }});

        m.put("denim", new HashMap<String, Double>() {{
            put("cotton",     0.90);
            put("denim",      0.60);
            put("wool",       0.75);
            put("polyester",  0.70);
            put("linen",      0.75);
            put("leather",    0.80);
            put("silk",       0.40);
            put("synthetic",  0.65);
        }});

        m.put("wool", new HashMap<String, Double>() {{
            put("cotton",     0.80);
            put("denim",      0.75);
            put("wool",       0.80);
            put("polyester",  0.65);
            put("linen",      0.60);
            put("leather",    0.75);
            put("silk",       0.70);
            put("synthetic",  0.60);
        }});

        m.put("polyester", new HashMap<String, Double>() {{
            put("cotton",     0.75);
            put("denim",      0.70);
            put("wool",       0.65);
            put("polyester",  0.75);
            put("linen",      0.70);
            put("leather",    0.60);
            put("silk",       0.55);
            put("synthetic",  0.80);
        }});

        m.put("linen", new HashMap<String, Double>() {{
            put("cotton",     0.85);
            put("denim",      0.75);
            put("wool",       0.60);
            put("polyester",  0.70);
            put("linen",      0.85);
            put("leather",    0.60);
            put("silk",       0.70);
            put("synthetic",  0.65);
        }});

        m.put("leather", new HashMap<String, Double>() {{
            put("cotton",     0.70);
            put("denim",      0.80);
            put("wool",       0.75);
            put("polyester",  0.60);
            put("linen",      0.60);
            put("leather",    0.70);
            put("silk",       0.65);
            put("synthetic",  0.60);
        }});

        m.put("silk", new HashMap<String, Double>() {{
            put("cotton",     0.65);
            put("denim",      0.40);
            put("wool",       0.70);
            put("polyester",  0.55);
            put("linen",      0.70);
            put("leather",    0.65);
            put("silk",       0.85);
            put("synthetic",  0.50);
        }});

        m.put("synthetic", new HashMap<String, Double>() {{
            put("cotton",     0.70);
            put("denim",      0.65);
            put("wool",       0.60);
            put("polyester",  0.80);
            put("linen",      0.65);
            put("leather",    0.60);
            put("silk",       0.50);
            put("synthetic",  0.80);
        }});

        return m;
    }

    private Map<String, Double> buildHueMap() {
        Map<String, Double> m = new HashMap<>();
        m.put("red",          0.0);
        m.put("dark red",     5.0);
        m.put("burgundy",     10.0);
        m.put("rust",         15.0);
        m.put("coral",        20.0);
        m.put("orange",       30.0);
        m.put("peach",        40.0);
        m.put("gold",         50.0);
        m.put("mustard",      55.0);
        m.put("yellow",       60.0);
        m.put("olive",        80.0);
        m.put("forest green", 120.0);
        m.put("green",        130.0);
        m.put("mint",         150.0);
        m.put("sage",         155.0);
        m.put("teal",         175.0);
        m.put("light blue",   195.0);
        m.put("blue",         210.0);
        m.put("navy",         225.0);
        m.put("dark navy",    230.0);
        m.put("lavender",     250.0);
        m.put("purple",       270.0);
        m.put("plum",         280.0);
        m.put("pink",         340.0);
        m.put("hot pink",     330.0);
        return m;
    }
}
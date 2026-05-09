import java.util.List;

public class ClothingItem {
    private String id;
    private String imagePath;

    // Core classification
    private ClothingType type;
    private String subType;
    private Formality formality;

    // Color
    private String primaryColor;
    private List<String> accentColors;
    private String colorFamily;

    // Style & Aesthetic
    private Pattern pattern;
    private List<String> styles;
    private String material;
    private String fit;

    // Context
    private List<String> suitableOccasions;

    // Constructor
    public ClothingItem(String id, String imagePath) {
        this.id = id;
        this.imagePath = imagePath;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public ClothingType getType() {
        return type;
    }

    public void setType(ClothingType type) {
        this.type = type;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public Formality getFormality() {
        return formality;
    }

    public void setFormality(Formality formality) {
        this.formality = formality;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public List<String> getAccentColors() {
        return accentColors;
    }

    public void setAccentColors(List<String> accentColors) {
        this.accentColors = accentColors;
    }

    public String getColorFamily() {
        return colorFamily;
    }

    public void setColorFamily(String colorFamily) {
        this.colorFamily = colorFamily;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public List<String> getStyles() {
        return styles;
    }

    public void setStyles(List<String> styles) {
        this.styles = styles;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getFit() {
        return fit;
    }

    public void setFit(String fit) {
        this.fit = fit;
    }

    public List<String> getSuitableOccasions() {
        return suitableOccasions;
    }

    public void setSuitableOccasions(List<String> suitableOccasions) {
        this.suitableOccasions = suitableOccasions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClothingItem)) return false;
        ClothingItem other = (ClothingItem) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}

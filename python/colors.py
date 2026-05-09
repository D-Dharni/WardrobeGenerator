from PIL import Image
import numpy as np
from sklearn.cluster import KMeans
import json
import sys

COLOR_NAMES = {
    # Reds
    "red": (255, 0, 0),
    "dark red": (139, 0, 0),
    "burgundy": (128, 0, 32),
    "pink": (255, 182, 193),
    "hot pink": (255, 105, 180),

    # Oranges and Yellows
    "orange": (255, 165, 0),
    "coral": (255, 127, 80),
    "peach": (255, 218, 185),
    "yellow": (255, 255, 0),
    "mustard": (255, 219, 88),
    "gold": (255, 215, 0),

    # Greens
    "green": (0, 128, 0),
    "olive": (128, 128, 0),
    "sage": (176, 192, 168),
    "mint": (189, 252, 201),
    "forest green": (34, 139, 34),

    # Blues
    "light blue": (173, 216, 230),
    "blue": (0, 0, 255),
    "navy": (0, 0, 128),
    "dark navy": (25, 35, 75),
    "teal": (0, 128, 128),

    # Purples
    "lavender": (230, 230, 250),
    "purple": (128, 0, 128),
    "plum": (142, 69, 133),

    # Browns and Tans
    "tan": (210, 180, 140),
    "brown": (139, 69, 19),
    "rust": (183, 65, 14),

    # Neutrals
    "white": (255, 255, 255),
    "cream": (255, 253, 208),
    "beige": (245, 245, 220),
    "light gray": (211, 211, 211),
    "gray": (128, 128, 128),
    "dark gray": (64, 64, 64),
    "charcoal": (54, 69, 79),
    "black": (0, 0, 0),
}

COLOR_FAMILIES = {
    # Warm
    "red": "warm", "dark red": "warm", "burgundy": "warm",
    "pink": "warm", "hot pink": "warm",
    "orange": "warm", "coral": "warm", "peach": "warm",
    "yellow": "warm", "mustard": "warm", "gold": "warm",
    "rust": "warm",

    # Cool
    "green": "cool", "olive": "cool", "sage": "cool",
    "mint": "cool", "forest green": "cool",
    "light blue": "cool", "blue": "cool", "navy": "cool",
    "dark navy": "cool", "teal": "cool",
    "lavender": "cool", "purple": "cool", "plum": "cool",

    # Neutral
    "white": "neutral", "cream": "neutral", "beige": "neutral",
    "tan": "neutral", "brown": "neutral",
    "light gray": "neutral", "gray": "neutral",
    "dark gray": "neutral", "charcoal": "neutral", "black": "neutral",
}

# Return the closest color name given an RGB value
def rgb_to_color_name(rgb: tuple) -> str:
    # Set the current distance to a math.max
    min_distance = float('inf')
    closest_color = "unknown"

    # Loop through the color names in the dictionary
    for color_name, color_rgb in COLOR_NAMES.items():
        # Measure how close the colors are related using Euclidean distance
        distance = sum((a - b) ** 2 for a, b in zip(rgb, color_rgb))

        # Check if it is lower than the lowest, if so replace it
        if distance < min_distance:
                min_distance = distance
                closest_color = color_name

    return closest_color

# Takes image path and returns dictionary with primary color, accent color, and color family
def extract_colors(image_path: str) -> dict:
    # Convert/open image and convert to numpy array for cluster analysis
    image = Image.open(image_path).convert("RGB")
    img_array = np.array(image)

    # Save the height and width of image and don't save the 3 RGB values
    h, w, _ = img_array.shape

    # Take only 10% of the height and width to try and counteract background interference
    margin_h = int(h * 0.25)
    margin_w = int(w * 0.25)

    # Numpy array slicing (cutting the image to our desired measurements)
    center = img_array[margin_h:h-margin_h, margin_w:w-margin_w]

    # Flatten the 2D array to a 1D array but each element is an array of 3 numbers (RGB)
    pixels = center.reshape(-1, 3)

    # Create a kmeans object that will find 5 clusters and run the algorithm
    kmeans = KMeans(n_clusters=5, random_state=42, n_init=10)
    kmeans.fit(pixels)

    # Create numpy array of the 5 center RGB clusters K-Means found
    cluster_centers = kmeans.cluster_centers_.astype(int)
    labels = kmeans.labels_

    # Count the number of pixels in each cluster
    counts = np.bincount(labels)

    # Put the biggest cluster index from most pixels to least
    sorted_indices = np.argsort(counts)[::-1]

    # Get and save the name of the highest pixel cluster
    primary_rgb = tuple(cluster_centers[sorted_indices[0]])
    primary_color = rgb_to_color_name(primary_rgb)

    # Start the loop from 1 because primary color is already set
    accent_colors = []
    for i in sorted_indices[1:]:
        # For each remaining cluster get its RGB value
        accent_rgb = tuple(cluster_centers[i])
        accent_name = rgb_to_color_name(accent_rgb)

        # Just skip if its the same name
        if accent_name != primary_color and accent_name not in accent_colors:
            accent_colors.append(accent_name)

    # If the primary color is in the color family it will return, otherwise it will return neutral
    color_family = COLOR_FAMILIES.get(primary_color, "neutral")

    # Return the final dictionary
    return {
        "primaryColor": primary_color,
        "accentColors": accent_colors,
        "colorFamily": color_family
    }

# Convert to JSON
if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "cropped.jpg"
    results = extract_colors(path)
    print(json.dumps(results))
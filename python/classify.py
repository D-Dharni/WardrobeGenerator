from PIL import Image
import torch
import open_clip
import json
import sys

# Label the lists for each attribute because the model is multimodal
TYPE_MAP = {
    "a top or upper body garment": "top",
    "a bottom or lower body garment": "bottom",
    "a dress or one piece": "dress",
    "an outerwear jacket or hoodie": "outerwear",
    "a pair of shoes or footwear": "shoes",
    "an accessory": "accessory"
}

SUBTYPE_MAP = {
    "a t-shirt with short sleeves": "t-shirt",
    "a blouse or dressy top": "blouse",
    "a button up shirt": "shirt",
    "a knit sweater or jumper": "sweater",
    "a hoodie with a hood": "hoodie",
    "a pair of jeans": "jeans",
    "a pair of trousers or dress pants": "trousers",
    "a pair of shorts": "shorts",
    "a skirt": "skirt",
    "a dress": "dress",
    "a zip up or pullover jacket": "jacket",
    "a long winter coat": "coat",
    "a formal blazer or sport coat": "blazer"
}

PATTERN_MAP = {
    "solid single color with no pattern": "solid",
    "horizontal or vertical stripes": "striped",
    "plaid or tartan pattern": "plaid",
    "floral print with flowers": "floral",
    "checkered pattern": "checkered",
    "graphic print or logo": "graphic print",
    "abstract pattern": "abstract"
}

MATERIAL_MAP = {
    "cotton fabric": "cotton",
    "denim fabric": "denim",
    "wool or knit fabric": "wool",
    "polyester or athletic fabric": "polyester",
    "linen fabric": "linen",
    "leather material": "leather",
    "silk or satin fabric": "silk",
    "synthetic or nylon fabric": "synthetic"
}

FIT_MAP = {
    "slim fit or skinny cut": "slim fit",
    "regular standard fit": "regular fit",
    "oversized or baggy fit": "oversized",
    "relaxed loose fit": "relaxed fit",
    "fitted or tailored cut": "fitted"
}

TYPE_LABELS = list(TYPE_MAP.keys())
SUBTYPE_LABELS = list(SUBTYPE_MAP.keys())
PATTERN_LABELS = list(PATTERN_MAP.keys())
MATERIAL_LABELS = list(MATERIAL_MAP.keys())
FIT_LABELS = list(FIT_MAP.keys())

def get_best_match(model, processor, tokenizer, image, labels: list) -> tuple:
    # Convert image to tensor
    image_tensor = processor(image).unsqueeze(0)

    # Convert list of string labels to tensor of numbers
    text_tokens = tokenizer(labels)

    # Convert to embeddings
    with torch.no_grad():
        image_features = model.encode_image(image_tensor, normalize=True)
        text_features = model.encode_text(text_tokens, normalize=True)

    # Score every label against the image
    scores = (100.0 * image_features @ text_features.T).softmax(dim=-1)

    # Find the index of the highest score
    best_index = scores.argmax().item()
    best_label = labels[best_index]
    best_score = scores[0][best_index].item()

    return best_label, best_score

def classify_clothing(image_path: str) -> dict:
    # Get the model in
    print("Loading model...")
    model, _, processor = open_clip.create_model_and_transforms('hf-hub:Marqo/marqo-fashionSigLIP')
    tokenizer = open_clip.get_tokenizer('hf-hub:Marqo/marqo-fashionSigLIP')

    image = Image.open(image_path).convert("RGB")
    print("Classifying...")

    # Get the result from the AI model
    type_label, _ = get_best_match(model, processor, tokenizer, image, TYPE_LABELS)
    subtype_label, _ = get_best_match(model, processor, tokenizer, image, SUBTYPE_LABELS)
    pattern_label, _ = get_best_match(model, processor, tokenizer, image, PATTERN_LABELS)
    material_label, _ = get_best_match(model, processor, tokenizer, image, MATERIAL_LABELS)
    fit_label, _ = get_best_match(model, processor, tokenizer, image, FIT_LABELS)

    # Map descriptive labels back to simple labels
    attributes = {
        "type": TYPE_MAP.get(type_label, type_label),
        "subType": SUBTYPE_MAP.get(subtype_label, subtype_label),
        "pattern": PATTERN_MAP.get(pattern_label, pattern_label),
        "material": MATERIAL_MAP.get(material_label, material_label),
        "fit": FIT_MAP.get(fit_label, fit_label)
    }

    return attributes

# Convert to JSON
if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "cropped.jpg"
    results = classify_clothing(path)
    print(json.dumps(results))
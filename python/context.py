from PIL import Image
import torch
import open_clip
import json
import sys

# Label the lists for each model because it is multimodal
FORMALITY_LABELS = ["casual everyday", "smart casual", "business casual", "formal", "athleisure sportswear"]

STYLE_LABELS = ["streetwear", "preppy", "minimalist", "bohemian", "classic",
                "athletic", "vintage", "casual", "business", "edgy"]

OCCASION_LABELS = ["everyday wear", "gym", "work", "weekend", "date night",
                   "party", "outdoor", "beach", "formal event", "loungewear"]

# Same as in classify.py
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

# Return multiple possible matches for labels
def get_top_matches(model, processor, tokenizer, image, labels: list, threshold: float = 0.15) -> list:
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

    # If the label is above the threshold then add it to the list
    results = []
    for i, label in enumerate(labels):
        score = scores[0][i].item()
        if score > threshold:
            results.append(label)

    # If results is empty, just take the best match possible
    if not results:
        results.append(labels[scores.argmax().item()])

    return results

def analyze_context(image_path: str) -> dict:
    # Load the model in
    print("Loading model...")
    model, _, processor = open_clip.create_model_and_transforms('hf-hub:Marqo/marqo-fashionSigLIP')
    tokenizer = open_clip.get_tokenizer('hf-hub:Marqo/marqo-fashionSigLIP')

    # Open and convert the image
    image = Image.open(image_path).convert("RGB")
    print("Analyzing context...")

    # Use get best match for this because formality is a scale with one answer
    # Use a tuple because normally it returns label and score but we only want label
    formality_label, _ = get_best_match(model, processor, tokenizer, image, FORMALITY_LABELS)

    # Use top matches because multiple categories can apply
    styles = get_top_matches(model, processor, tokenizer, image, STYLE_LABELS)
    occasions = get_top_matches(model, processor, tokenizer, image, OCCASION_LABELS)

    # Build and return the output dictionary
    context = {
        "formality": formality_label,
        "styles": styles,
        "suitableOccasions": occasions
    }

    return context

# Convert to JSON
if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "cropped.jpg"
    results = analyze_context(path)
    print(json.dumps(results))
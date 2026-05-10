import anthropic
import base64
import json
import sys
from PIL import Image
import io

def encode_image(image_path: str) -> tuple:
    # Open and convert the image to RGB
    image = Image.open(image_path).convert("RGB")

    # Save it to a bytes buffer as JPEG
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG")
    buffer.seek(0)

    # Encode the bytes as base64 so we can send it to the API
    image_data = base64.standard_b64encode(buffer.read()).decode("utf-8")
    return image_data, "image/jpeg"

def classify_clothing(image_path: str) -> dict:
    # Encode the image
    image_data, media_type = encode_image(image_path)

    # Create the Anthropic client
    client = anthropic.Anthropic()

    # Send the image to Claude with a classification prompt
    message = client.messages.create(
        model="claude-opus-4-5",
        max_tokens=1024,
        # Message has two components: image and prompt
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": media_type,
                            "data": image_data,
                        },
                    },
                    {
                        "type": "text",
                        "text": """Analyze this clothing item and respond with ONLY a JSON object, no other text.

The JSON must have exactly these fields:
- type: one of [top, bottom, dress, outerwear, shoes, accessory]
  * top = t-shirts, shirts, blouses, tank tops, sweaters worn as a base layer
  * bottom = pants, jeans, shorts, skirts
  * outerwear = hoodies, zip-ups, jackets, coats, blazers, anything worn OVER other clothes
  * dress = one piece dresses
  * shoes = any footwear
  * accessory = bags, hats, jewelry
- subType: one of [t-shirt, blouse, shirt, sweater, hoodie, jeans, trousers, shorts, skirt, dress, jacket, coat, blazer]
- pattern: one of [solid, striped, plaid, floral, checkered, graphic print, abstract]
- material: one of [cotton, denim, wool, polyester, linen, leather, silk, synthetic]
- fit: one of [slim fit, regular fit, oversized, relaxed fit, fitted]

Example response:
{"type": "top", "subType": "t-shirt", "pattern": "solid", "material": "cotton", "fit": "oversized"}"""
                    }
                ],
            }
        ],
    )

    # Extract the text response and parse it as JSON
    response_text = message.content[0].text.strip()

    # Remove any markdown code blocks if Claude added them
    if response_text.startswith("```"):
        response_text = response_text.split("```")[1]
        if response_text.startswith("json"):
            response_text = response_text[4:]
    response_text = response_text.strip()

    return json.loads(response_text)

if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "cropped.jpg"
    results = classify_clothing(path)
    print(json.dumps(results))
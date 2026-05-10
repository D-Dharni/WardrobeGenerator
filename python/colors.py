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

def extract_colors(image_path: str) -> dict:
    # Encode the image
    image_data, media_type = encode_image(image_path)

    # Create the Anthropic client
    client = anthropic.Anthropic()

    # Send the image to Claude with a color extraction prompt
    message = client.messages.create(
        model="claude-opus-4-5",
        max_tokens=1024,
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
                        "text": """Analyze the colors of this clothing item and respond with ONLY a JSON object, no other text. Ignore the background — only analyze the clothing item itself.

The JSON must have exactly these fields:
- primaryColor: the single most dominant color of the clothing item, as a simple color name like "navy", "white", "gray", "black", "dark navy", "light blue", etc.
- accentColors: a list of up to 3 secondary colors visible on the item (can be empty list if none)
- colorFamily: one of [warm, cool, neutral]

warm = reds, oranges, yellows, pinks, corals
cool = blues, greens, purples, teals
neutral = black, white, gray, beige, brown, tan, cream

Example response:
{"primaryColor": "navy", "accentColors": ["white", "gray"], "colorFamily": "cool"}"""
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
    results = extract_colors(path)
    print(json.dumps(results))
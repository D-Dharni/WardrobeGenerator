from flask import Flask, send_file, jsonify
import json
import os
import glob

# Create a flask application instance
app = Flask(__name__)

WARDROBE_PATH = "/Users/devendharni/IdeaProjects/WardrobeGenerator/wardrobe/"

# Getting the clothing item data
@app.route('/items')
def get_items():
    items = []
    for json_file in glob.glob(WARDROBE_PATH + "*.json"):
        with open(json_file) as f:
            items.append(json.load(f))
    return jsonify(items)

# For the frontend to actually load the clothing items
@app.route('/image/<path:filename>')
def get_image(filename):
    return send_file(WARDROBE_PATH + filename)

# To make sure the frontend doesn't get blocked
@app.after_request
def add_cors(response):
    response.headers['Access-Control-Allow-Origin'] = '*'
    return response

# For hardcoding the outfits for now
@app.route('/outfits')
def get_outfits():
    outfits = [
        {
            "score": 85.9,
            "items": ["item_010", "item_007", "item_004"]
        },
        {
            "score": 85.7,
            "items": ["item_007", "item_009", "item_004"]
        },
        {
            "score": 85.4,
            "items": ["item_002", "item_007", "item_004"]
        }
    ]
    return jsonify(outfits)

if __name__ == '__main__':
    app.run(port=5000)
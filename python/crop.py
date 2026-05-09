from ultralytics import YOLO
import PIL.Image as Image
import sys

# Crop a piece of clothing for image pre-processing
def crop_clothing(image_path: str, output_path: str) -> str:
    # Create a YOLO object with the specific model
    model = YOLO("yolov8n.pt")

    # Run the image through the model
    results = model(image_path)

    # Load in the image file from path
    image = Image.open(image_path).convert("RGB")

    # Keep variables to try and keep track of what the best image box is
    best_box = None
    best_score = 0

    # Loop through each detected object in the image
    for result in results:
        for box in result.boxes:
            # Extract the score
            confidence = float(box.conf)

            # Update if relevant
            if confidence > best_score:
                best_score = confidence
                best_box = box

    # If nothing was detected just return the original image
    if best_box is None:
        print ("No object detected, using original image")
        image.save(output_path)
        return output_path

    # Get the coordinates of the best box
    x1, y1, x2, y2 = map(int, best_box.xyxy[0])

    # Crop the image and then save those coordinates
    cropped = image.crop((x1, y1, x2, y2))
    cropped.save(output_path)

    print(f"Cropped image saved to {output_path}")
    return output_path

if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "test.jpg"
    crop_clothing(path, "cropped.jpg")
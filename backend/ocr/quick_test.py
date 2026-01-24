import os
from paddleocr import PaddleOCR

os.environ["DISABLE_MODEL_SOURCE_CHECK"] = "True"

ocr = PaddleOCR(lang="en", use_textline_orientation=True)

res = ocr.predict("test.jpg")
page = res[0]  # one page

texts = page.get("rec_texts", [])
scores = page.get("rec_scores", [])

print(f"Lines detected: {len(texts)}")
print("-" * 60)

for t, s in zip(texts, scores):
    print(f"{s:0.2f}  {t}")

import argparse
import base64
import io
import json
import os
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "data" / "clip-model" / "vision_model.onnx"
INDEX_DIR = ROOT / "data" / "anime-index"
GRAPHQL = "https://graphql.anilist.co"
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)


def session():
    return ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])


def preprocess(image):
    image = image.convert("RGB")
    width, height = image.size
    scale = 224 / min(width, height)
    image = image.resize((round(width * scale), round(height * scale)), Image.Resampling.BICUBIC)
    left = (image.width - 224) // 2
    top = (image.height - 224) // 2
    image = image.crop((left, top, left + 224, top + 224))
    pixels = np.asarray(image, dtype=np.float32) / 255.0
    pixels = (pixels - MEAN) / STD
    return np.transpose(pixels, (2, 0, 1))[None]


def embed(model, image):
    vector = model.run(["image_embeds"], {"pixel_values": preprocess(image)})[0][0]
    return vector / max(np.linalg.norm(vector), 1e-12)


def gql(query, variables):
    body = json.dumps({"query": query, "variables": variables}).encode()
    request = urllib.request.Request(GRAPHQL, body, {"Content-Type": "application/json", "User-Agent": "AnimeAI/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)["data"]


def download_image(url):
    request = urllib.request.Request(url, headers={"User-Agent": "AnimeAI/1.0"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return Image.open(io.BytesIO(response.read())).copy()


def build(pages, anime_ids):
    INDEX_DIR.mkdir(parents=True, exist_ok=True)
    model = session()
    metadata = []
    vectors = []
    fields = "id title{native romaji english} characters(page:1,perPage:12,sort:[ROLE,RELEVANCE]){edges{role node{id name{full native}image{large}}}}"
    media_query = "query($page:Int!){Page(page:$page,perPage:50){media(type:ANIME,sort:POPULARITY_DESC){" + fields + "}}}"
    selected_query = "query($ids:[Int]){Page(perPage:50){media(id_in:$ids,type:ANIME){" + fields + "}}}"
    seen = set()
    batches = []
    if anime_ids:
        batches.append(gql(selected_query, {"ids": anime_ids})["Page"]["media"])
    for page in range(1, pages + 1):
        batches.append(gql(media_query, {"page": page})["Page"]["media"])
    for page, media_list in enumerate(batches, 1):
        for media in media_list:
            titles = media["title"]
            title = titles.get("native") or titles.get("english") or titles.get("romaji")
            for edge in media["characters"]["edges"]:
                node = edge["node"]
                key = (media["id"], node["id"])
                if key in seen or not node.get("image", {}).get("large"):
                    continue
                seen.add(key)
                try:
                    vectors.append(embed(model, download_image(node["image"]["large"])))
                    metadata.append({"animeId": media["id"], "anime": title, "characterId": node["id"], "character": node["name"].get("native") or node["name"]["full"], "role": edge["role"]})
                except Exception as error:
                    print(f"skip {title}/{node['name']['full']}: {error}", flush=True)
        print(f"indexed batch {page}/{len(batches)}: {len(metadata)} characters", flush=True)
        time.sleep(1)
    np.save(INDEX_DIR / "vectors.npy", np.asarray(vectors, dtype=np.float32))
    (INDEX_DIR / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False), encoding="utf-8")


class SearchServer:
    def __init__(self):
        self.model = session()
        self.vectors = np.load(INDEX_DIR / "vectors.npy")
        self.metadata = json.loads((INDEX_DIR / "metadata.json").read_text(encoding="utf-8"))

    def search(self, encoded, limit=8):
        image = Image.open(io.BytesIO(base64.b64decode(encoded.split(",", 1)[-1])))
        scores = self.vectors @ embed(self.model, image)
        indexes = np.argsort(scores)[-limit:][::-1]
        return [{**self.metadata[int(index)], "score": round(float(scores[index]), 4)} for index in indexes]


def serve(port):
    searcher = SearchServer()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.reply(200, {"status": "ok", "size": len(searcher.metadata)})

        def do_POST(self):
            try:
                length = int(self.headers.get("Content-Length", "0"))
                payload = json.loads(self.rfile.read(length))
                self.reply(200, {"matches": searcher.search(payload["image"], payload.get("limit", 8))})
            except Exception as error:
                self.reply(400, {"error": str(error)})

        def reply(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    build_parser = sub.add_parser("build")
    build_parser.add_argument("--pages", type=int, default=20)
    build_parser.add_argument("--anime-id", type=int, action="append", default=[])
    serve_parser = sub.add_parser("serve")
    serve_parser.add_argument("--port", type=int, default=8094)
    args = parser.parse_args()
    build(args.pages, args.anime_id) if args.command == "build" else serve(args.port)

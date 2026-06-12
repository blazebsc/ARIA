import base64
import struct
import logging
import argparse
import os
from contextlib import asynccontextmanager

import numpy as np
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("whisper-server")

model = None
MODEL_SIZE = os.environ.get("WHISPER_MODEL", "base")


def pcm_to_float(pcm_bytes: bytes) -> np.ndarray:
    num_samples = len(pcm_bytes) // 2
    samples = struct.unpack(f"<{num_samples}h", pcm_bytes)
    return np.array(samples, dtype=np.float32) / 32768.0


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    from faster_whisper import WhisperModel

    logger.info("Loading Whisper model: %s", MODEL_SIZE)
    model = WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8")
    logger.info("Model loaded.")
    yield


app = FastAPI(title="Faster Whisper STT Server", lifespan=lifespan)


class TranscriptionRequest(BaseModel):
    model: str = "base"
    audio: str
    response_format: str = "json"


@app.post("/v1/audio/transcriptions")
def transcribe(req: TranscriptionRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    try:
        pcm_bytes = base64.b64decode(req.audio)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 audio")

    audio = pcm_to_float(pcm_bytes)
    segments, info = model.transcribe(audio, beam_size=5)
    text = "".join(seg.text for seg in segments).strip()

    logger.info("Transcribed (%.1fs audio): %s", info.duration, text[:80])
    return {"text": text}


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_SIZE}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Faster Whisper STT Server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8181)
    parser.add_argument("--model", default=MODEL_SIZE)
    args = parser.parse_args()

    MODEL_SIZE = args.model
    uvicorn.run(app, host=args.host, port=args.port)

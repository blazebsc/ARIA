import io
import os
import logging
import argparse
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import numpy as np
import soundfile as sf
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, model_validator

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("f5-tts-server")

f5tts = None
F5_DEVICE = os.environ.get("F5_DEVICE", "auto")
REF_AUDIO = os.environ.get("F5_REF_AUDIO", "voice.wav")
REF_TEXT  = os.environ.get("F5_REF_TEXT",  "Some call me nature, others call me mother nature")
OUTPUT_DIR = os.environ.get("F5_OUTPUT_DIR", "tts_output")
NFE_STEP = int(os.environ.get("F5_NFE_STEP", "16"))


def load_f5tts_model():
    from f5_tts.api import F5TTS
    import torch

    device = F5_DEVICE.strip().lower()
    if device in ("", "auto"):
        device = None
    elif device not in ("cuda", "cpu"):
        raise ValueError(f"Unsupported F5 device: {F5_DEVICE!r}")

    label = device or "auto"
    logger.info("Loading F5-TTS model (ref_audio=%s, device=%s)...", REF_AUDIO, label)
    try:
        return F5TTS(device=device)
    except torch.cuda.OutOfMemoryError:
        if device == "cpu":
            raise
        logger.warning("CUDA OOM while loading F5-TTS; retrying on CPU")
        torch.cuda.empty_cache()
        return F5TTS(device="cpu")


@asynccontextmanager
async def lifespan(app: FastAPI):
    global f5tts
    install_soundfile_torchaudio_loader()
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    f5tts = load_f5tts_model()
    logger.info("F5-TTS model loaded on %s.", f5tts.device)
    yield


app = FastAPI(title="F5-TTS Voice Server", lifespan=lifespan)


def install_soundfile_torchaudio_loader() -> None:
    """Avoid torchcodec-backed torchaudio.load for local WAV files."""
    import torch
    import torchaudio

    original_load = torchaudio.load

    def load_with_soundfile(
        uri,
        frame_offset: int = 0,
        num_frames: int = -1,
        normalize: bool = True,
        channels_first: bool = True,
        format=None,
        buffer_size: int = 4096,
        backend=None,
    ):
        if not isinstance(uri, (str, os.PathLike)):
            return original_load(uri, frame_offset, num_frames, normalize, channels_first, format, buffer_size, backend)

        frames = -1 if num_frames is None or num_frames < 0 else num_frames
        dtype = "float32" if normalize else "int16"
        audio, sample_rate = sf.read(uri, start=frame_offset, frames=frames, dtype=dtype, always_2d=True)
        tensor = torch.from_numpy(audio.T.copy() if channels_first else audio.copy())
        return tensor, sample_rate

    torchaudio.load = load_with_soundfile
    logger.info("Using soundfile-backed torchaudio.load for local audio files.")


class SpeechRequest(BaseModel):
    model: str = "tts-1"
    voice: str = "aria"
    response_format: str = "wav"

    # Accept either "input" (OpenAI-style) or "text" (what AriaTtsManager sends)
    input: Optional[str] = None
    text:  Optional[str] = None
    speed: float = 1.5

    @model_validator(mode="after")
    def require_one_text_field(self) -> "SpeechRequest":
        if not (self.input or self.text):
            raise ValueError("Provide either 'input' or 'text'")
        return self

    @property
    def gen_text(self) -> str:
        return (self.input or self.text or "").strip()


def to_wav_bytes(audio: np.ndarray, sr: int = 24000) -> bytes:
    buf = io.BytesIO()
    sf.write(buf, audio, sr, format="WAV", subtype="PCM_16")
    return buf.getvalue()


@app.post("/v1/audio/speech")
@app.post("/tts")                          # also listen on /tts for the mod
def synthesize(req: SpeechRequest):
    if f5tts is None:
        raise HTTPException(503, "F5-TTS model not loaded")
    if not req.gen_text:
        raise HTTPException(400, "Empty text")

    logger.info("Synthesising (%d chars, %.1fx): %.80s", len(req.gen_text), req.speed, req.gen_text)

    try:
        wav, sr, _ = f5tts.infer(
            ref_file=REF_AUDIO,
            ref_text=REF_TEXT,   # empty string → F5-TTS auto-transcribes (uses more VRAM)
            gen_text=req.gen_text,
            speed=req.speed,
            nfe_step=NFE_STEP,
        )
    except Exception as exc:
        logger.error("TTS inference failed: %s", exc, exc_info=True)
        raise HTTPException(500, f"TTS error: {exc}")

    wav_bytes = to_wav_bytes(wav, sr)

    out_path = Path(OUTPUT_DIR) / "last_output.wav"
    out_path.write_bytes(wav_bytes)

    logger.info("Done: %d bytes, %.2fs", len(wav_bytes), len(wav) / sr)
    return Response(content=wav_bytes, media_type="audio/wav")


@app.get("/health")
def health():
    device = getattr(f5tts, "device", None) if f5tts is not None else None
    return {"status": "ok", "ref_audio": REF_AUDIO, "device": device}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="F5-TTS Voice Server")
    parser.add_argument("--ref_audio", default=REF_AUDIO,
                        help="Path to reference .wav (your recorded voice)")
    parser.add_argument("--ref_text",  default=REF_TEXT,
                        help="Exact transcript of the reference audio")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument(
        "--device",
        default=F5_DEVICE,
        choices=["auto", "cuda", "cpu"],
        help="Inference device (auto picks CUDA when available)",
    )
    parser.add_argument(
        "--nfe_step",
        type=int,
        default=NFE_STEP,
        help="Number of inference steps (lower = faster, default: 16)",
    )
    args = parser.parse_args()

    # Update globals before uvicorn starts the lifespan
    REF_AUDIO = args.ref_audio
    REF_TEXT  = args.ref_text
    F5_DEVICE = args.device
    NFE_STEP = args.nfe_step

    if not os.path.exists(REF_AUDIO):
        logger.error("Reference audio not found: %s", REF_AUDIO)
        logger.info("Put your reference voice at ./voice.wav or pass --ref_audio")
        exit(1)

    uvicorn.run(app, host=args.host, port=args.port)

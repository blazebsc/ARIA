#!/usr/bin/env python3
"""Set up and run ARIA's local AI services.

Put a reference voice file named voice.wav in this directory, then run:

    python start_aria.py

The script starts Ollama, Faster Whisper, and F5-TTS.
Press Ctrl+C to stop anything this script started.
"""

from __future__ import annotations

import argparse
import base64
import contextlib
import json
import os
import struct
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
F5_DIR = ROOT / "F5-TTS"
VENV_DIR = F5_DIR / ".venv"
LOG_DIR = ROOT / ".aria_logs"

DEFAULT_OLLAMA_PORT = 11434
DEFAULT_WHISPER_PORT = 8181
DEFAULT_F5_PORT = 8080
DEFAULT_OLLAMA_MODEL = "hermes3:8b"
DEFAULT_WHISPER_MODEL = "base"
F5_MIN_VRAM_MIB = 1500

REQUIRED_IMPORTS = (
    "fastapi",
    "uvicorn",
    "faster_whisper",
    "soundfile",
    "f5_tts.api",
)


@dataclass
class StartedProcess:
    name: str
    process: subprocess.Popen[Any]
    log_file: Any
    log_path: Path


started: list[StartedProcess] = []


def status(message: str) -> None:
    print(f"[aria] {message}", flush=True)


def fail(message: str, code: int = 1) -> None:
    print(f"[aria] ERROR: {message}", file=sys.stderr, flush=True)
    raise SystemExit(code)


def venv_python() -> Path:
    if os.name == "nt":
        return VENV_DIR / "Scripts" / "python.exe"
    return VENV_DIR / "bin" / "python"


def run_checked(cmd: list[str], cwd: Path = ROOT, env: dict[str, str] | None = None) -> None:
    status("running: " + " ".join(cmd))
    subprocess.run(cmd, cwd=cwd, env=env, check=True)


def capture(cmd: list[str], cwd: Path = ROOT, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def server_env(extra: dict[str, str] | None = None) -> dict[str, str]:
    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"
    env.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
    existing = env.get("PYTHONPATH")
    src_path = str(F5_DIR / "src")
    env["PYTHONPATH"] = src_path if not existing else src_path + os.pathsep + existing
    if extra:
        env.update(extra)
    return env


def gpu_free_mib() -> int | None:
    nvidia_smi = shutil.which("nvidia-smi")
    if not nvidia_smi:
        return None
    result = capture([nvidia_smi, "--query-gpu=memory.free", "--format=csv,noheader,nounits"])
    if result.returncode != 0:
        return None
    try:
        return int(result.stdout.strip().splitlines()[0].strip())
    except (ValueError, IndexError):
        return None


def resolve_f5_device(requested: str) -> str:
    choice = requested.strip().lower()
    if choice in ("cuda", "cpu"):
        return choice
    if choice not in ("", "auto"):
        fail(f"unknown F5 device {requested!r}; use auto, cuda, or cpu")

    free = gpu_free_mib()
    if free is None:
        return "auto"

    if free < F5_MIN_VRAM_MIB:
        status(
            f"only {free} MiB GPU memory free; using CPU for F5-TTS "
            f"(needs ~{F5_MIN_VRAM_MIB} MiB — close other GPU apps or pass --f5-device cuda to force GPU)"
        )
        return "cpu"

    status(f"GPU memory free: {free} MiB")
    return "cuda"


def python_has_deps(python: Path) -> bool:
    imports = "; ".join(f"__import__({name!r})" for name in REQUIRED_IMPORTS)
    result = capture([str(python), "-c", imports], env=server_env())
    if result.returncode != 0:
        status("Python dependency check failed:")
        print(result.stdout.strip(), flush=True)
        return False
    return True


def find_host_python() -> str:
    for name in ("python3.11", "python3.10", "python3.12", "python3.13", "python3.14", "python3", "python"):
        path = shutil.which(name)
        if not path:
            continue
        result = capture([path, "-c", "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"])
        if result.returncode == 0:
            major, minor = result.stdout.strip().split(".", 1)
            if int(major) == 3 and int(minor) >= 10:
                return path
    fail("Python 3.10+ is required for the voice services.")


def create_venv() -> None:
    VENV_DIR.parent.mkdir(parents=True, exist_ok=True)
    uv = shutil.which("uv")
    if uv:
        run_checked([uv, "venv", "--python", "3.11", str(VENV_DIR)])
        return
    host_python = find_host_python()
    run_checked([host_python, "-m", "venv", str(VENV_DIR)])


def install_python_deps(python: Path) -> None:
    run_checked([str(python), "-m", "pip", "install", "--upgrade", "pip", "setuptools", "wheel"])
    run_checked([str(python), "-m", "pip", "install", "-e", str(F5_DIR)])
    run_checked(
        [
            str(python),
            "-m",
            "pip",
            "install",
            "fastapi",
            "uvicorn[standard]",
            "faster-whisper",
            "soundfile",
            "numpy",
            "pydantic>=2",
        ]
    )
    run_checked(
        [
            str(python),
            "-m",
            "pip",
            "install",
            "--force-reinstall",
            "--no-deps",
            "tqdm>=4.60",
        ]
    )


def ensure_python(setup: bool) -> Path:
    python = venv_python()
    if python.exists() and python_has_deps(python):
        status(f"using Python env: {python}")
        return python

    if not setup:
        fail("Python voice dependencies are missing. Run without --no-setup to install them.")

    if not python.exists():
        status("creating Python environment for voice services")
        create_venv()

    python = venv_python()
    if not python.exists():
        fail(f"venv Python was not created at {python}")

    status("installing/updating Python voice dependencies")
    install_python_deps(python)

    if not python_has_deps(python):
        fail("Python voice dependencies still do not import. Check the pip output above.")

    return python


def request_json(url: str, timeout: float = 2.0) -> dict[str, Any] | None:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            if not body:
                return {}
            return json.loads(body)
    except (OSError, urllib.error.URLError, json.JSONDecodeError):
        return None


def post_json(url: str, payload: dict[str, Any], timeout: float = 30.0) -> dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8", errors="replace")
        return json.loads(body) if body else {}


def wait_for_http(name: str, url: str, timeout: float, proc: subprocess.Popen[Any] | None = None) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        data = request_json(url, timeout=2.0)
        if data is not None:
            status(f"{name} is ready")
            return
        if proc is not None and proc.poll() is not None:
            fail(f"{name} exited while starting. See logs for details.")
        time.sleep(1.0)
    fail(f"{name} did not become ready at {url} within {int(timeout)}s")


def is_http_ready(url: str) -> bool:
    return request_json(url, timeout=1.0) is not None


def start_process(name: str, cmd: list[str], cwd: Path = ROOT, env: dict[str, str] | None = None) -> StartedProcess:
    LOG_DIR.mkdir(exist_ok=True)
    log_path = LOG_DIR / f"{name}.log"
    log_file = log_path.open("ab", buffering=0)
    header = f"\n\n===== {time.strftime('%Y-%m-%d %H:%M:%S')} {' '.join(cmd)} =====\n"
    log_file.write(header.encode("utf-8"))
    status(f"starting {name}; log: {log_path}")
    proc = subprocess.Popen(
        cmd,
        cwd=cwd,
        env=env,
        stdout=log_file,
        stderr=subprocess.STDOUT,
        start_new_session=(os.name != "nt"),
    )
    entry = StartedProcess(name=name, process=proc, log_file=log_file, log_path=log_path)
    started.append(entry)
    return entry


def terminate_process(entry: StartedProcess, sig: int) -> None:
    proc = entry.process
    if proc.poll() is not None:
        return
    with contextlib.suppress(ProcessLookupError):
        if os.name == "nt":
            proc.terminate()
        else:
            os.killpg(proc.pid, sig)


def stop_started_processes() -> None:
    if not started:
        return
    status("stopping services started by this script")
    for entry in reversed(started):
        terminate_process(entry, signal.SIGTERM)

    deadline = time.monotonic() + 10.0
    for entry in reversed(started):
        remaining = max(0.1, deadline - time.monotonic())
        try:
            entry.process.wait(timeout=remaining)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            pass

    for entry in reversed(started):
        if entry.process.poll() is None:
            terminate_process(entry, signal.SIGKILL)
            try:
                entry.process.wait(timeout=2.0)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                pass
        with contextlib.suppress(Exception):
            entry.log_file.close()


def ensure_voice_file(ref_audio: Path) -> None:
    if not ref_audio.exists():
        fail(
            f"reference voice file not found: {ref_audio}\n"
            "Put your voice sample at ./voice.wav, or pass --ref-audio /path/to/file.wav."
        )
    if ref_audio.suffix.lower() != ".wav":
        fail(f"reference voice file must be a .wav file: {ref_audio}")


def read_wav_as_16k_mono_pcm(path: Path) -> bytes:
    with wave.open(str(path), "rb") as wav:
        channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        sample_rate = wav.getframerate()
        frame_count = wav.getnframes()
        raw = wav.readframes(frame_count)

    if sample_width != 2:
        fail(f"{path} must be 16-bit PCM WAV; got {sample_width * 8}-bit samples")
    if channels < 1:
        fail(f"{path} has no audio channels")

    sample_count = len(raw) // 2
    samples = list(struct.unpack(f"<{sample_count}h", raw))
    if channels > 1:
        samples = [
            int(sum(samples[i : i + channels]) / channels)
            for i in range(0, len(samples) - channels + 1, channels)
        ]

    target_rate = 16000
    if sample_rate != target_rate and samples:
        new_count = max(1, int(len(samples) * target_rate / sample_rate))
        resampled: list[int] = []
        for i in range(new_count):
            pos = i * sample_rate / target_rate
            left = int(pos)
            right = min(left + 1, len(samples) - 1)
            fraction = pos - left
            value = samples[left] * (1.0 - fraction) + samples[right] * fraction
            resampled.append(max(-32768, min(32767, int(value))))
        samples = resampled

    return struct.pack(f"<{len(samples)}h", *samples)


def transcribe_reference_audio(ref_audio: Path, whisper_port: int, whisper_model: str) -> str:
    status("auto-transcribing voice.wav for F5-TTS reference text")
    pcm = read_wav_as_16k_mono_pcm(ref_audio)
    try:
        result = post_json(
            f"http://127.0.0.1:{whisper_port}/v1/audio/transcriptions",
            {
                "model": whisper_model,
                "audio": base64.b64encode(pcm).decode("ascii"),
                "response_format": "json",
            },
            timeout=120.0,
        )
    except Exception as exc:
        fail(f"could not auto-transcribe {ref_audio}: {exc}. Pass --ref-text manually.")

    text = str(result.get("text", "")).strip()
    if not text:
        fail(f"Whisper returned an empty transcript for {ref_audio}. Pass --ref-text manually.")

    preview = text if len(text) <= 100 else text[:97] + "..."
    status(f"transcribed: {text}")
    return text


def ensure_mod_config(ollama_port: int, whisper_port: int, f5_port: int) -> None:
    config_path = ROOT / "run" / "config" / "aria-common.toml"
    config_path.parent.mkdir(parents=True, exist_ok=True)
    if config_path.exists():
        lines = config_path.read_text(encoding="utf-8").splitlines()
    else:
        lines = ["[audio]"]

    wanted = {
        "ollamaPort": ollama_port,
        "whisperPort": whisper_port,
        "f5TtsPort": f5_port,
    }
    seen: set[str] = set()
    rewritten: list[str] = []
    for line in lines:
        stripped = line.strip()
        replaced = False
        for key, value in wanted.items():
            if stripped.startswith(f"{key} ="):
                indent = line[: len(line) - len(line.lstrip())]
                rewritten.append(f"{indent}{key} = {value}")
                seen.add(key)
                replaced = True
                break
        if not replaced:
            rewritten.append(line)

    if "[audio]" not in [line.strip() for line in rewritten]:
        rewritten.insert(0, "[audio]")

    if len(seen) < len(wanted):
        try:
            audio_index = next(i for i, line in enumerate(rewritten) if line.strip() == "[audio]")
        except StopIteration:
            audio_index = 0
        insert_at = audio_index + 1
        for key, value in wanted.items():
            if key not in seen:
                rewritten.insert(insert_at, f"\t{key} = {value}")
                insert_at += 1

    new_text = "\n".join(rewritten).rstrip() + "\n"
    old_text = config_path.read_text(encoding="utf-8") if config_path.exists() else ""
    if new_text != old_text:
        config_path.write_text(new_text, encoding="utf-8")
        status(f"updated mod service ports in {config_path}")


def ensure_ollama(port: int, model: str, pull_model: bool) -> None:
    ollama = shutil.which("ollama")
    if not ollama:
        fail("ollama was not found on PATH. Install Ollama first.")

    env = os.environ.copy()
    env["OLLAMA_HOST"] = f"127.0.0.1:{port}"
    url = f"http://127.0.0.1:{port}/api/version"
    if is_http_ready(url):
        status("Ollama is already running")
    else:
        entry = start_process("ollama", [ollama, "serve"], env=env)
        wait_for_http("Ollama", url, timeout=60, proc=entry.process)

    if not model:
        return

    result = capture([ollama, "list"], env=env)
    if result.returncode != 0:
        fail("could not list Ollama models:\n" + result.stdout.strip())

    models = {line.split()[0] for line in result.stdout.splitlines()[1:] if line.split()}
    if model in models:
        status(f"Ollama model available: {model}")
        return

    if not pull_model:
        fail(f"Ollama model {model!r} is missing. Re-run without --skip-model-pull.")

    status(f"pulling Ollama model: {model}")
    run_checked([ollama, "pull", model], env=env)


def ensure_whisper(python: Path, host: str, port: int, model: str) -> None:
    url = f"http://127.0.0.1:{port}/health"
    if is_http_ready(url):
        status(f"Whisper is already running on port {port}")
        return

    entry = start_process(
        "whisper",
        [
            str(python),
            str(ROOT / "whisper_server.py"),
            "--host",
            host,
            "--port",
            str(port),
            "--model",
            model,
        ],
        env=server_env({"WHISPER_MODEL": model}),
    )
    wait_for_http("Whisper", url, timeout=180, proc=entry.process)


def ensure_f5_tts(
    python: Path,
    host: str,
    port: int,
    ref_audio: Path,
    ref_text: str,
    device: str,
    nfe_step: int = 16,
) -> None:
    url = f"http://127.0.0.1:{port}/health"
    existing = request_json(url, timeout=1.0)
    if existing is not None:
        status(f"F5-TTS is already running on port {port}")
        running_ref = str(existing.get("ref_audio", ""))
        if running_ref and Path(running_ref).name != ref_audio.name:
            status(f"F5-TTS is using {running_ref}, not {ref_audio}")
        return

    entry = start_process(
        "f5-tts",
        [
            str(python),
            str(ROOT / "f5_tts_server.py"),
            "--host",
            host,
            "--port",
            str(port),
            "--ref_audio",
            str(ref_audio),
            "--ref_text",
            ref_text,
            "--device",
            device,
            "--nfe_step",
            str(nfe_step),
        ],
        env=server_env(
            {
                "F5_REF_AUDIO": str(ref_audio),
                "F5_REF_TEXT": ref_text,
                "F5_DEVICE": device,
            }
        ),
    )
    wait_for_http("F5-TTS", url, timeout=600, proc=entry.process)


def wait_for_services() -> None:
    status("services are running. Press Ctrl+C to stop.")
    last_gpu_print = 0.0
    while True:
        for entry in started:
            if entry.process.poll() is not None:
                fail(f"{entry.name} exited unexpectedly. See {entry.log_path}")
        now = time.monotonic()
        if now - last_gpu_print >= 1.0:
            free = gpu_free_mib()
            if free is not None:
                print(f"\r[aria] GPU memory free: {free} MiB", end="", flush=True)
            last_gpu_print = now
        time.sleep(1.0)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Set up and run ARIA's local services.")
    parser.add_argument("--ref-audio", default="voice.wav", help="Reference voice WAV for F5-TTS.")
    parser.add_argument("--ref-text", default=os.environ.get("F5_REF_TEXT", ""), help="Exact transcript of ref audio.")
    parser.add_argument("--host", default="127.0.0.1", help="Host for local Python services.")
    parser.add_argument("--ollama-port", type=int, default=DEFAULT_OLLAMA_PORT)
    parser.add_argument("--whisper-port", type=int, default=DEFAULT_WHISPER_PORT)
    parser.add_argument("--f5-port", type=int, default=DEFAULT_F5_PORT)
    parser.add_argument("--ollama-model", default=os.environ.get("ARIA_OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL))
    parser.add_argument("--whisper-model", default=os.environ.get("ARIA_WHISPER_MODEL", DEFAULT_WHISPER_MODEL))
    parser.add_argument(
        "--f5-device",
        default=os.environ.get("F5_DEVICE", "auto"),
        choices=["auto", "cuda", "cpu"],
        help="Device for F5-TTS (auto uses CPU when GPU memory is low).",
    )
    parser.add_argument("--no-setup", action="store_true", help="Do not create/update the Python environment.")
    parser.add_argument("--skip-model-pull", action="store_true", help="Fail instead of pulling a missing Ollama model.")
    parser.add_argument("--nfe-step", type=int, default=16, help="F5-TTS inference steps (lower = faster, default: 16)")
    return parser.parse_args()


def interactive_setup(args: argparse.Namespace) -> None:
    if sys.stdin.isatty():
        print("\n[aria] Voice setup", flush=True)
        wav_path = input(f"  WAV file path [{args.ref_audio}]: ").strip()
        if wav_path:
            args.ref_audio = wav_path

        transcript = input("  Transcript (or press Enter to auto-transcribe): ").strip()
        if transcript:
            args.ref_text = transcript
        else:
            args.ref_text = ""
        print(flush=True)


def main() -> int:
    args = parse_args()
    interactive_setup(args)

    ref_audio = Path(args.ref_audio)
    if not ref_audio.is_absolute():
        ref_audio = ROOT / ref_audio
    ref_audio = ref_audio.resolve()

    ensure_voice_file(ref_audio)
    python = ensure_python(setup=not args.no_setup)
    ensure_mod_config(args.ollama_port, args.whisper_port, args.f5_port)
    ensure_ollama(args.ollama_port, args.ollama_model, pull_model=not args.skip_model_pull)
    ensure_whisper(python, args.host, args.whisper_port, args.whisper_model)
    if not args.ref_text.strip():
        args.ref_text = transcribe_reference_audio(ref_audio, args.whisper_port, args.whisper_model)
    f5_device = resolve_f5_device(args.f5_device)
    ensure_f5_tts(python, args.host, args.f5_port, ref_audio, args.ref_text, f5_device, nfe_step=args.nfe_step)

    wait_for_services()
    return 0


if __name__ == "__main__":
    interrupted = False
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        interrupted = True
        status("interrupted")
    finally:
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        stop_started_processes()
    raise SystemExit(130 if interrupted else 0)

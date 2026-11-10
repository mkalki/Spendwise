"""Bake a subtle soft-focus copy so scrolling needs no GPU blur filters."""
from pathlib import Path
import subprocess
import imageio_ffmpeg

media = Path(__file__).resolve().parents[1] / 'public' / 'media'
subprocess.run([
    imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-loglevel', 'error',
    '-i', str(media / 'spendwise-world.mp4'),
    '-vf', 'gblur=sigma=0.8', '-c:v', 'libx264', '-crf', '23',
    '-preset', 'fast', '-an', '-movflags', '+faststart',
    str(media / 'spendwise-world-soft.mp4'),
], check=True)

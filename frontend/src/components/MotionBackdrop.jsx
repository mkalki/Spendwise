import { useEffect, useRef } from 'react';

export default function MotionBackdrop({ paused, reduced, crisp = false }) {
  const video = useRef(null);
  useEffect(() => {
    const player = video.current;
    function playback() {
      if (paused || reduced || document.hidden) player.pause();
      else player.play().catch(() => {});
    }
    playback();
    document.addEventListener('visibilitychange', playback);
    return () => { player.pause(); document.removeEventListener('visibilitychange', playback); };
  }, [paused, reduced, crisp]);
  return <div className={`studio-motion-backdrop ${crisp ? 'is-crisp' : ''}`} aria-hidden="true"><video ref={video} src={crisp ? "/media/spendwise-world.mp4" : "/media/spendwise-world-soft.mp4"} poster="/media/spendwise-world-poster.jpg" muted loop playsInline preload="metadata"/><div className="studio-film-tint"/><div className="studio-film-texture"/></div>;
}

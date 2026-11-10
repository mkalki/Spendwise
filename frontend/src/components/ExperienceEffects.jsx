import { useEffect, useState } from "react";
import BrandMark from "./BrandMark";
import MotionBackdrop from "./MotionBackdrop";
import { useReducedMotion } from "./useReducedMotion";

export function ExperienceLoader({ ready }) {
  const reduced = useReducedMotion();
  const [visible, setVisible] = useState(true);
  const [exiting, setExiting] = useState(false);
  const [cycle, setCycle] = useState(0);

  useEffect(() => {
    function replay() { setVisible(true); setExiting(false); setCycle(value => value + 1); }
    window.addEventListener("spendwise:replay-intro", replay);
    return () => window.removeEventListener("spendwise:replay-intro", replay);
  }, []);

  useEffect(() => {
    if (!ready) return undefined;
    const exitTimer = window.setTimeout(() => setExiting(true), 650);
    const timer = window.setTimeout(() => setVisible(false), 1250);
    return () => { window.clearTimeout(exitTimer); window.clearTimeout(timer); };
  }, [ready, cycle]);

  if (!visible) return null;

  return (
    <div className={`experience-loader ${ready ? "is-ready" : ""} ${exiting ? "is-exiting" : ""}`} role="status" aria-live="polite" aria-busy={!ready}>
      <MotionBackdrop paused={false} reduced={reduced}/>
      <div className="cinematic-loader-scan" />
      <div className="loader-wire-cube" aria-hidden="true">{Array.from({length:6}, (_, index) => <i key={index}/>)}</div>
      <span className="loader-corner top">SW / PERSONAL BUYING UNIVERSE</span>
      <div className="cinematic-loader-orbit" aria-hidden="true" />
      <div className="cinematic-loader-orbit second" aria-hidden="true" />
      <div className="cinematic-loader-emblem"><BrandMark /></div>
      <div className="cinematic-loader-wordmark">SPEND<span>WISE</span></div>
      <div className="cinematic-loader-subtitle">YOUR NEXT DIMENSION IS LOADING</div>
      <div className="loader-system-state"><span>{ready ? "SESSION CONNECTED" : "CONNECTING SESSION"}</span><i/><span>{ready ? "EXPERIENCE READY" : "PREPARING EXPERIENCE"}</span></div>
      <div className="loader-track">
        <span />
      </div>
      <div className="loader-status">{ready ? "Entering your workspace" : "Preparing your workspace"}</div>
      <span className="loader-corner bottom">YOUR WORLD. YOUR RULES.</span>
    </div>
  );
}

export function CustomCursor() {
  useEffect(() => {
    const fine = window.matchMedia('(pointer: fine)');
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    const cursor = document.createElement('div');
    cursor.className = 'studio-reticle';
    cursor.setAttribute('aria-hidden', 'true');
    cursor.innerHTML = '<span class="reticle-frame"><i></i><i></i><i></i><i></i></span><span class="reticle-orbit"></span><span class="reticle-dot"></span><span class="reticle-label"></span>';
    document.body.appendChild(cursor);
    const label = cursor.querySelector('.reticle-label');
    let frame = null, x = -100, y = -100, enabled = false;
    function preference() {
      enabled = fine.matches && !reduce.matches;
      document.body.classList.toggle('has-studio-reticle', enabled);
      cursor.hidden = !enabled;
    }
    function paint() {
      frame = null;
      cursor.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      cursor.classList.add('is-visible');
    }
    function move(event) {
      if (!enabled) return;
      x = event.clientX; y = event.clientY;
      // At most one position update per input frame, with no trailing loop.
      if (frame === null) frame = requestAnimationFrame(paint);
    }
    function hover(event) {
      if (!(event.target instanceof Element)) return;
      const editing = Boolean(event.target.closest('input, textarea, select, [contenteditable="true"]'));
      const dragging = Boolean(event.target.closest('.product-scene'));
      const interactive = Boolean(event.target.closest('a, button, [role="button"]'));
      cursor.dataset.mode = editing ? 'edit' : dragging ? 'drag' : interactive ? 'link' : 'idle';
      label.textContent = dragging ? 'ROTATE' : interactive ? 'EXPLORE' : '';
    }
    function down() { cursor.classList.add('is-pressed'); }
    function up() { cursor.classList.remove('is-pressed'); }
    function leave() { cursor.classList.remove('is-visible'); up(); }
    preference();
    fine.addEventListener('change', preference); reduce.addEventListener('change', preference);
    window.addEventListener('pointermove', move, { passive: true });
    document.addEventListener('pointerover', hover, { passive: true });
    document.addEventListener('pointerdown', down, { passive: true });
    document.addEventListener('pointerup', up, { passive: true });
    document.documentElement.addEventListener('pointerleave', leave);
    window.addEventListener('blur', leave);
    return () => {
      if (frame !== null) cancelAnimationFrame(frame);
      cursor.remove(); document.body.classList.remove('has-studio-reticle');
      fine.removeEventListener('change', preference); reduce.removeEventListener('change', preference);
      window.removeEventListener('pointermove', move); document.removeEventListener('pointerover', hover);
      document.removeEventListener('pointerdown', down); document.removeEventListener('pointerup', up);
      document.documentElement.removeEventListener('pointerleave', leave); window.removeEventListener('blur', leave);
    };
  }, []);
  return null;
}

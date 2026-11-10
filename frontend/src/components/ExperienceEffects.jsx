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

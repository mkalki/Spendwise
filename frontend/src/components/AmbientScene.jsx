import { useEffect, useRef } from "react";

export default function AmbientScene() {
  const sceneRef = useRef(null);

  useEffect(() => {
    const scene = sceneRef.current;

    function updatePointer(event) {
      scene.style.setProperty(
        "--pointer-x",
        `${(event.clientX / window.innerWidth - 0.5) * 2}`
      );
      scene.style.setProperty(
        "--pointer-y",
        `${(event.clientY / window.innerHeight - 0.5) * 2}`
      );
    }

    window.addEventListener("pointermove", updatePointer, { passive: true });

    return () => {
      window.removeEventListener("pointermove", updatePointer);
    };
  }, []);

  return (
    <div className="ambient-scene" ref={sceneRef} aria-hidden="true">
      <div className="scene-noise" />
      <div className="scene-glow scene-glow-one" />
      <div className="scene-glow scene-glow-two" />
      <div className="scene-orb scene-orb-one" />
      <div className="scene-orb scene-orb-two" />
      <div className="scene-ring scene-ring-one" />
      <div className="scene-ring scene-ring-two" />
      <div className="scene-grid" />
      <div className="scene-scanline" />
    </div>
  );
}

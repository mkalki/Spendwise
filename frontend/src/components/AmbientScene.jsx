import { useEffect } from "react";

export default function AmbientScene() {
  useEffect(() => {
    function updatePointer(event) {
      document.documentElement.style.setProperty(
        "--pointer-x",
        `${(event.clientX / window.innerWidth - 0.5) * 2}`
      );
      document.documentElement.style.setProperty(
        "--pointer-y",
        `${(event.clientY / window.innerHeight - 0.5) * 2}`
      );
    }

    window.addEventListener("pointermove", updatePointer, { passive: true });
    function updateScroll() {
      document.documentElement.style.setProperty("--scroll-y", window.scrollY);
    }

    window.addEventListener("scroll", updateScroll, { passive: true });
    updateScroll();

    return () => {
      window.removeEventListener("pointermove", updatePointer);
      window.removeEventListener("scroll", updateScroll);
    };
  }, []);

  return (
    <div className="ambient-scene" aria-hidden="true">
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

import { useEffect, useState } from "react";

export function ExperienceLoader({ ready }) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (!ready) return undefined;
    const timer = window.setTimeout(() => setVisible(false), 900);
    return () => window.clearTimeout(timer);
  }, [ready]);

  if (!visible) return null;

  return (
    <div className={`experience-loader ${ready ? "is-ready" : ""}`}>
      <div className="loader-mark">SW</div>
      <div className="loader-copy">
        <span>Spendwise</span>
        <small>Protected buying studio</small>
      </div>
      <div className="loader-track">
        <span />
      </div>
      <div className="loader-status">{ready ? "Entering your workspace" : "Preparing your workspace"}</div>
    </div>
  );
}

export function CustomCursor() {
  useEffect(() => {
    if (window.matchMedia("(pointer: coarse)").matches) return undefined;

    const cursor = document.createElement("div");
    cursor.className = "custom-cursor";
    document.body.appendChild(cursor);

    function move(event) {
      cursor.style.setProperty("--cursor-x", `${event.clientX}px`);
      cursor.style.setProperty("--cursor-y", `${event.clientY}px`);
    }

    function toggleHover(event) {
      const target = event.target;
      cursor.classList.toggle(
        "is-hovering",
        Boolean(target.closest("a, button, input, select, textarea, [role='button']"))
      );
    }

    window.addEventListener("pointermove", move, { passive: true });
    document.addEventListener("pointerover", toggleHover, { passive: true });
    return () => {
      window.removeEventListener("pointermove", move);
      document.removeEventListener("pointerover", toggleHover);
      cursor.remove();
    };
  }, []);

  return null;
}

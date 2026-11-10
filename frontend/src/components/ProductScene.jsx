import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

export default function ProductScene({ color = '#d8dce6', autoFinish = false, paused = false, scrollTarget, rotationLabel, resetKey = 0, cinematic = false }) {
  const host = useRef(null);
  const settings = useRef({ color, autoFinish, paused, resetKey });
  const [failed, setFailed] = useState(false);
  useEffect(() => { settings.current = { color, autoFinish, paused, resetKey }; }, [color, autoFinish, paused, resetKey]);

  useEffect(() => {
    const container = host.current;
    let renderer;
    try { renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' }); }
    catch { queueMicrotask(() => setFailed(true)); return; }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.25));
    renderer.setClearColor(0, 0);
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.1;
    renderer.domElement.setAttribute('aria-hidden', 'true');
    container.appendChild(renderer.domElement);
    const scene = new THREE.Scene();
    const pmrem = new THREE.PMREMGenerator(renderer);
    const room = new RoomEnvironment();
    const environment = pmrem.fromScene(room, .04);
    scene.environment = environment.texture;
    room.dispose();
    const camera = new THREE.PerspectiveCamera(cinematic ? 34 : 36, 1, .1, 80);
    camera.position.set(0, .25, cinematic ? 8.1 : 8.6);
    const controls = new OrbitControls(camera, renderer.domElement);
    renderer.domElement.style.touchAction = 'pan-y';
    controls.enableZoom = false;
    controls.enablePan = false;
    controls.enableDamping = true;
    controls.rotateSpeed = .55;
    controls.minPolarAngle = Math.PI * .25;
    controls.maxPolarAngle = Math.PI * .75;
    controls.saveState();
    scene.add(new THREE.AmbientLight(0xb8bfe1, .35));
    const key = new THREE.DirectionalLight(0xf2eeff, 4);
    key.position.set(3, 5, 4); scene.add(key);
    const red = new THREE.PointLight(0xff183e, 45); red.position.set(-3, 1, 2); scene.add(red);
    const blue = new THREE.PointLight(0x506dff, 55); blue.position.set(3, 0, 1); scene.add(blue);
    const assembly = new THREE.Group(); scene.add(assembly);
    const shell = new THREE.MeshPhysicalMaterial({ color: settings.current.color, metalness: .92, roughness: .19, clearcoat: 1, clearcoatRoughness: .13 });
    const dark = new THREE.MeshStandardMaterial({ color: 0x101019, roughness: .84, metalness: .08 });
    const chrome = new THREE.MeshPhysicalMaterial({ color: 0xaab2c8, metalness: 1, roughness: .15 });
    const trim = new THREE.MeshStandardMaterial({ color: 0x750c23, metalness: .75, roughness: .25 });
    function mesh(geometry, material, parent = assembly) { const object = new THREE.Mesh(geometry, material); parent.add(object); return object; }
    const band = mesh(new THREE.TorusGeometry(1.14, .11, 24, 128, Math.PI), shell);
    band.position.y = .25; band.scale.z = 1.7;
    const pad = mesh(new THREE.TorusGeometry(.975, .085, 24, 100, Math.PI), dark);
    pad.position.y = .25; pad.scale.z = 1.7;
    const seam = mesh(new THREE.TorusGeometry(1.14, .014, 10, 100, Math.PI), trim);
    seam.position.set(0, .25, .18);
    const badge = document.createElement('canvas'); badge.width = 128; badge.height = 128;
    const context = badge.getContext('2d');
    context.fillStyle = '#161722'; context.fillRect(0, 0, 128, 128);
    context.fillStyle = '#d9deee'; context.font = 'italic bold 44px Arial'; context.textAlign = 'center'; context.fillText('SW', 64, 78);
    const badgeTexture = new THREE.CanvasTexture(badge);
    const badgeMaterial = new THREE.MeshStandardMaterial({ map: badgeTexture, roughness: .55, metalness: .4 });
    for (const side of [-1, 1]) {
      const rail = mesh(new THREE.CylinderGeometry(.045, .045, .52, 24), chrome);
      rail.position.set(side * 1.1, .07, 0);
      const cup = new THREE.Group(); cup.position.set(side * 1.12, -.43, 0); cup.rotation.z = side * -.1; assembly.add(cup);
      const body = mesh(new THREE.CapsuleGeometry(.37, .25, 16, 48), shell, cup); body.scale.set(.72, 1, 1.1);
      const cushion = mesh(new THREE.TorusGeometry(.29, .105, 28, 64), dark, cup);
      cushion.rotation.y = Math.PI / 2; cushion.position.x = -side * .23; cushion.scale.y = 1.37;
      const inside = mesh(new THREE.CircleGeometry(.29, 48), dark, cup);
      inside.rotation.y = side * Math.PI / 2; inside.position.x = -side * .225; inside.scale.y = 1.37; inside.material.side = THREE.DoubleSide;
      const accentRing = mesh(new THREE.TorusGeometry(.257, .012, 12, 64), chrome, cup);
      accentRing.rotation.y = Math.PI / 2; accentRing.position.x = side * .273; accentRing.scale.y = 1.35;
      const badgeMesh = mesh(new THREE.CircleGeometry(.205, 48), badgeMaterial, cup);
      badgeMesh.rotation.y = side * Math.PI / 2; badgeMesh.position.x = side * .278;
      // Recessed acoustic perforations use instancing rather than hundreds of draw calls.
      const holes = new THREE.InstancedMesh(new THREE.SphereGeometry(.009, 6, 4), dark, 68);
      const dummy = new THREE.Object3D();
      for (let i = 0; i < 68; i++) { const angle = i / 68 * Math.PI * 2; dummy.position.set(side * .281, Math.cos(angle) * .226 * 1.35, Math.sin(angle) * .226); dummy.updateMatrix(); holes.setMatrixAt(i, dummy.matrix); }
      cup.add(holes);
      for (let i = 0; i < 2; i++) {
        const button = mesh(new THREE.BoxGeometry(.04, .075, .065), chrome, cup);
        button.position.set(side * .18, -.14 + i * .12, .32);
      }
    }
    const haloMaterial = new THREE.MeshBasicMaterial({ color: 0xff274c, transparent: true, opacity: .65 });
    const halo = mesh(new THREE.TorusGeometry(2.02, .009, 8, 180), haloMaterial, scene);
    halo.rotation.set(.4, .65, -.25); halo.position.z = -.75;
    const secondHalo = mesh(new THREE.TorusGeometry(2.1, .004, 8, 160), new THREE.MeshBasicMaterial({ color: 0x6a7fff, transparent: true, opacity: .5 }), scene);
    secondHalo.rotation.set(-.4, -.6, .25); secondHalo.position.z = -.8;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    let visible = true;
    const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; }); observer.observe(container);
    const resize = new ResizeObserver(() => {
      const { width, height } = container.getBoundingClientRect();
      renderer.setSize(width, height); camera.aspect = width / Math.max(height, 1);
      camera.position.z = width < 500 ? 9.5 : cinematic ? 8.1 : 8.6;
      camera.updateProjectionMatrix();
    }); resize.observe(container);
    let frame, lastReset = settings.current.resetKey, angle = -.48, progress = 0;
    function measureScroll() {
      const section = scrollTarget?.current;
      if (!section) return;
      const rect = section.getBoundingClientRect();
      progress = THREE.MathUtils.clamp(-rect.top / Math.max((rect.height - window.innerHeight) * .98, 1), 0, 1);
    }
    measureScroll();
    window.addEventListener('scroll', measureScroll, { passive: true });
    window.addEventListener('resize', measureScroll, { passive: true });
    const started = performance.now();
    let previousFrame = started;
    const finishStops = ['#d8dce6', '#3158c9', '#b31839'].map(color => new THREE.Color(color));
    const targetColor = new THREE.Color();
    function render(now = performance.now()) {
      frame = requestAnimationFrame(render);
      if (!visible || document.hidden) return;
      const delta = Math.min(64, now - previousFrame); previousFrame = now;
      const follow = 1 - Math.exp(-delta / 70);
      const { color: finish, paused: frozen, resetKey: reset } = settings.current;
      if (reset !== lastReset) { controls.reset(); lastReset = reset; }

      const t = (performance.now() - started) / 1000;
      const section = scrollTarget?.current;
      if (settings.current.autoFinish && !reduced.matches) {
        if (!frozen) {
          const position = progress * (finishStops.length - 1);
          const index = Math.min(finishStops.length - 2, Math.floor(position));
          targetColor.copy(finishStops[index]).lerp(finishStops[index + 1], position - index);
          shell.color.lerp(targetColor, follow);
        }
      } else shell.color.lerp(targetColor.set(finish), follow);
      container.dataset.finish = shell.color.getHexString();
      if (!reduced.matches && !frozen) {
        const target = section ? -.48 + progress * Math.PI * 2 : -.48 + Math.sin(t * .3) * .25;
        angle = THREE.MathUtils.lerp(angle, target, follow);
        assembly.rotation.set(.12 + Math.sin(progress * Math.PI) * .18, angle, -.18 + progress * .36);
        assembly.position.y = Math.sin(t * .7) * .065;
        halo.rotation.z = -.25 + progress * Math.PI * .6 + t * .025;
        secondHalo.rotation.y = -.6 - progress;
      }
      const degrees = Math.round((angle + .48) / (Math.PI * 2) * 360);
      if (rotationLabel?.current) rotationLabel.current.textContent = `${String(Math.max(0, degrees)).padStart(3, '0')}°`;
      container.dataset.rotation = String(degrees);
      controls.update(); renderer.render(scene, camera);
    } render();
    return () => {
      window.removeEventListener('scroll', measureScroll); window.removeEventListener('resize', measureScroll);
      cancelAnimationFrame(frame); resize.disconnect(); observer.disconnect(); controls.dispose();
      const geometries = new Set(), materials = new Set();
      scene.traverse(object => { if (object.geometry) geometries.add(object.geometry); if (object.material) materials.add(object.material); });
      geometries.forEach(geometry => geometry.dispose()); materials.forEach(material => material.dispose());
      badgeTexture.dispose(); environment.dispose(); pmrem.dispose(); renderer.dispose(); renderer.domElement.remove();
    };
  }, [cinematic, scrollTarget, rotationLabel]); // Settings update without rebuilding the scene.
  return <div ref={host} className="product-scene" role="img" aria-label="Reflective 3D Spendwise headphones. Scroll to turn the model, or drag to inspect it.">{failed && <div className="scene-fallback"><img src="/media/product-002.jpg" alt="Spendwise headphone concept"/><span>3D is unavailable on this device. Your dashboard still works.</span></div>}</div>;
}

import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

export default function ProductScene({ color = '#c5f36a', paused = false }) {
  const host = useRef(null);
  const material = useRef(null);
  const pause = useRef(paused);
  const [failed, setFailed] = useState(false);
  useEffect(() => { pause.current = paused; }, [paused]);
  useEffect(() => { if (material.current) material.current.color.set(color); }, [color]);
  useEffect(() => {
    const container = host.current;
    let renderer;
    try { renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true }); }
    catch { queueMicrotask(() => setFailed(true)); return; }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75));
    renderer.setClearColor(0x000000, 0);
    container.appendChild(renderer.domElement);
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(36, 1, .1, 100);
    camera.position.set(0, .35, 8.6);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableZoom = false;
    controls.enablePan = false;
    controls.enableDamping = true;
    controls.minPolarAngle = Math.PI * .25;
    controls.maxPolarAngle = Math.PI * .75;
    scene.add(new THREE.AmbientLight(0xc9d4eb, 2));
    const key = new THREE.DirectionalLight(0xffffff, 5);
    key.position.set(3, 5, 4); scene.add(key);
    const rim = new THREE.PointLight(0xc5f36a, 65); rim.position.set(-3, 1, 2); scene.add(rim);
    const blue = new THREE.PointLight(0x7689ff, 60); blue.position.set(3, -1, -1); scene.add(blue);
    const group = new THREE.Group(); scene.add(group);
    const shell = new THREE.MeshPhysicalMaterial({ color: 0xc5f36a, metalness: .62, roughness: .25, clearcoat: 1 });
    material.current = shell;
    const dark = new THREE.MeshStandardMaterial({ color: 0x151a16, roughness: .52, metalness: .35 });
    const chrome = new THREE.MeshStandardMaterial({ color: 0xc7cfc8, metalness: .95, roughness: .2 });
    const band = new THREE.Mesh(new THREE.TorusGeometry(1.12, .16, 24, 100, Math.PI), shell);
    band.position.y = .35; group.add(band);
    const inner = new THREE.Mesh(new THREE.TorusGeometry(.94, .09, 16, 80, Math.PI), dark);
    inner.position.y = .35; group.add(inner);
    for (const side of [-1, 1]) {
      const joint = new THREE.Mesh(new THREE.CylinderGeometry(.075, .075, .65, 20), chrome);
      joint.position.set(side * 1.1, .12, 0); group.add(joint);
      const cup = new THREE.Group(); cup.position.set(side * 1.12, -.43, 0); cup.rotation.z = side * -.12;
      const body = new THREE.Mesh(new THREE.CapsuleGeometry(.36, .36, 12, 36), shell);
      body.scale.set(.6, 1, 1); cup.add(body);
      const cushion = new THREE.Mesh(new THREE.TorusGeometry(.3, .115, 20, 48), dark);
      cushion.rotation.y = Math.PI / 2; cushion.position.x = -side * .21; cushion.scale.y = 1.45; cup.add(cushion);
      const disc = new THREE.Mesh(new THREE.CylinderGeometry(.23, .23, .035, 40), chrome);
      disc.rotation.z = Math.PI / 2; disc.position.x = side * .23; cup.add(disc);
      group.add(cup);
    }
    group.rotation.set(.08, -.38, -.2);
    const orbit = new THREE.Mesh(new THREE.TorusGeometry(2.18, .008, 8, 140), new THREE.MeshBasicMaterial({ color: 0x778668, transparent: true, opacity: .5 }));
    orbit.rotation.set(1.1, .3, -.3); scene.add(orbit);
    const points = new Float32Array(150 * 3);
    for (let i = 0; i < points.length; i++) points[i] = (Math.random() - .5) * 12;
    const stars = new THREE.Points(new THREE.BufferGeometry().setAttribute('position', new THREE.BufferAttribute(points, 3)), new THREE.PointsMaterial({ color: 0xb5c99b, size: .015, transparent: true, opacity: .5 }));
    scene.add(stars);
    const resize = new ResizeObserver(() => {
      const { width, height } = container.getBoundingClientRect();
      renderer.setSize(width, height); camera.aspect = width / Math.max(height, 1); camera.updateProjectionMatrix();
    }); resize.observe(container);
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const clock = new THREE.Clock();
    let frame;
    let visible = true;
    const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; }); observer.observe(container);
    function render() {
      frame = requestAnimationFrame(render);
      if (!visible || document.hidden) return;
      const t = clock.getElapsedTime();
      if (!reduced && !pause.current) { group.position.y = Math.sin(t * .8) * .12; group.rotation.y = -.38 + Math.sin(t * .3) * .28; orbit.rotation.z = t * .05; }
      controls.update(); renderer.render(scene, camera);
    } render();
    return () => {
      cancelAnimationFrame(frame); resize.disconnect(); observer.disconnect(); controls.dispose();
      scene.traverse(object => { object.geometry?.dispose(); if (object.material) object.material.dispose(); });
      renderer.dispose(); renderer.domElement.remove(); material.current = null;
    };
  }, []);
  return <div ref={host} className="product-scene" role="img" aria-label="Interactive 3D headphones. Drag to rotate.">{failed && <div className="scene-fallback">◉<span>Your next great find.</span></div>}</div>;
}

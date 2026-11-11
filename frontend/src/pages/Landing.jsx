import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowUpRight, Maximize2, Minimize2, Pause, Play, ShieldCheck, Sparkles, SlidersHorizontal, X } from 'lucide-react';
import AuthPage from './AuthPage';
import './Landing.css';

const chapters = ['Discover', 'Your world', 'Your rules'];
const objects = [
  { image: 'product-001.jpg', number: '001', category: 'EVERYDAY / REIMAGINED', title: 'A step ahead.', description: 'Find the pair that fits your life and your limits.' },
  { image: 'product-002.jpg', number: '002', category: 'SOUND / REDEFINED', title: 'Your own frequency.', description: 'Less noise. More of what you’re looking for.' },
  { image: 'product-003.jpg', number: '003', category: 'ESSENTIALS / ELEVATED', title: 'Carry less impulse.', description: 'Discover something worth making room for.' },
];

export default function Landing() {
  const [auth, setAuth] = useState(() => new URLSearchParams(window.location.search).get('auth') === 'login');
  const [paused, setPaused] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [immersive, setImmersive] = useState(false);
  const [chapter, setChapter] = useState(0);
  const [mediaError, setMediaError] = useState(false);
  const video = useRef(null);
  const dialog = useRef(null);
  const page = useRef(null);

  useEffect(() => {
    const player = video.current;
    function syncPlayback() {
      if (paused || auth || document.hidden) player.pause();
      else player.play().catch(error => {
        if (error.name !== 'AbortError') setPaused(true);
      });
    }
    syncPlayback();
    document.addEventListener('visibilitychange', syncPlayback);
    return () => document.removeEventListener('visibilitychange', syncPlayback);
  }, [paused, auth]);

  useEffect(() => {
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const change = event => setPaused(event.matches);
    preference.addEventListener('change', change);
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) if (entry.isIntersecting) setChapter(Number(entry.target.dataset.chapter));
    }, { rootMargin: '-40% 0px -40% 0px', threshold: 0 });
    page.current.querySelectorAll('[data-chapter]').forEach(section => observer.observe(section));
    return () => { preference.removeEventListener('change', change); observer.disconnect(); };
  }, []);

  useEffect(() => {
    if (!auth && !immersive) return;
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    if (auth) dialog.current?.querySelector('button')?.focus();
    function keydown(event) {
      if (event.key === 'Escape') { setAuth(false); setImmersive(false); }
      if (!auth || event.key !== 'Tab') return;
      const items = dialog.current.querySelectorAll('button, a[href], input, select, textarea');
      const first = items[0], last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
    document.addEventListener('keydown', keydown);
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown', keydown); previous?.focus(); };
  }, [auth, immersive]);

  function pointer(event) {
    if (paused || event.pointerType === 'touch') return;
    page.current.style.setProperty('--film-x', `${(event.clientX / window.innerWidth - .5) * -12}px`);
    page.current.style.setProperty('--film-y', `${(event.clientY / window.innerHeight - .5) * -8}px`);
  }

  return <div ref={page} onPointerMove={pointer} className={`film-landing ${immersive ? 'is-immersive' : ''}`}>
    <div className="film-backdrop" aria-hidden="true">
      <video ref={video} className="world-film" poster="/media/spendwise-world-poster.jpg" muted loop playsInline preload="auto" onError={() => { setMediaError(true); setPaused(true); }}>
        <source src="/media/spendwise-world.mp4" type="video/mp4" />
      </video>
      <div className="film-vignette"/><div className="film-scanlines"/><div className="film-grain"/>
    </div>
    <div className="film-content" inert={auth || immersive}>
      <header className="film-header">
        <a href="#discover" className="film-brand" aria-label="Spendwise home"><svg viewBox="0 0 60 40" aria-hidden="true"><path d="M4 10 29 15 18 0 55 23 34 21 45 40 4 17 23 20Z" fill="currentColor"/></svg><span>SPENDWISE</span><sup>®</sup></a>
        <p className="film-intro">A world of possibilities. An intelligence that gets you.<br/>Discover your next great find, with your spending rules<br className="desktop-break"/> built into every decision.</p>
        <button className="film-button film-enter" onClick={() => setAuth(true)}>MY WORKSPACE <ArrowUpRight size={17}/></button>
      </header>
      <main>
        <section className="film-chapter film-hero" id="discover" data-chapter="0" aria-labelledby="film-title">
          <div className="film-timecode"><span className="record-light"/> SW WORLD / VOL. 001 <span>ORIGINAL 3D FILM</span></div>
          <div className="film-title-group"><p>THE NEXT GENERATION OF SHOPPING</p><h1 id="film-title">SPEND<span>WISE</span></h1><div className="film-title-bottom"><span>DESIRE, MEET INTENTION.</span><span>AI RECOMMENDS. YOU DECIDE.</span></div></div>
          <span className="film-side-note" aria-hidden="true">DISCOVER / WITHOUT LIMITS<br/>SPEND / WITH INTENTION</span>
          <a className="film-scroll" href="#your-world"><span>ENTER THE WORLD</span><ArrowDown size={18}/></a>
        </section>
        <section className="film-chapter film-world" id="your-world" data-chapter="1" aria-labelledby="world-title">
          <div className="film-section-heading"><span className="film-index">01 — DISCOVERY, REDEFINED</span><h2 id="world-title">YOUR TASTE.<br/><span>YOUR UNIVERSE.</span></h2><p>Tell us what you’re looking for.<br/>Your assistant finds real products.<br/>You choose what deserves a place in your world.</p></div>
          <div className="film-objects">{objects.map(object => <button className="film-object" key={object.number} onClick={() => setAuth(true)} aria-label={`Discover products: ${object.title}`}><div className="film-object-image"><img src={`/media/${object.image}`} alt={object.category.startsWith('EVERYDAY') ? 'Original silver and red 3D sneaker concept' : object.number === '002' ? 'Original metallic 3D headphones concept' : 'Original metallic shopping bag concept'} loading="lazy"/><span className="object-number">SW / {object.number}</span><ArrowUpRight className="object-arrow" size={22}/></div><div className="film-object-description"><span>{object.category}</span><h3>{object.title}</h3><p>{object.description}</p></div></button>)}</div>
          <p className="film-concept-note">3D concept collection. Sign in to discover live products.</p>
        </section>
        <section className="film-chapter film-rules" id="your-rules" data-chapter="2" aria-labelledby="rules-title">
          <span className="film-index">02 — INTELLIGENCE WITH BOUNDARIES</span><h2 id="rules-title">YOUR WORLD.<br/><span>YOUR RULES.</span></h2>
          <div className="film-rule-list">{[{ Icon: Sparkles, number: '01', title: 'Discover with AI.', copy: 'Describe what you want. Explore products matched to your intent.' }, { Icon: SlidersHorizontal, number: '02', title: 'Define your limits.', copy: 'Set your budget. Spending rules are checked before checkout.' }, { Icon: ShieldCheck, number: '03', title: 'You make the call.', copy: 'Review and approve a clearly labelled PayPal Sandbox payment.' }].map(({ Icon, number, title, copy }) => <article key={number}><div><span>{number}</span><Icon size={21}/></div><h3>{title}</h3><p>{copy}</p></article>)}</div>
          <button className="film-button film-start" onClick={() => setAuth(true)}>ENTER YOUR WORKSPACE <ArrowUpRight size={18}/></button>
          <p className="film-sandbox-note">Product discovery and Sandbox payments are separate.<br/>A Sandbox payment does not buy the product from its retailer.</p>
        </section>
      </main>
    </div>
    <footer className="film-hud" inert={auth}>
      <div className="film-project-label"><span>SPENDWISE SHOPPING PROJECT</span><small>{mediaError ? 'STILL FRAME / VIDEO UNAVAILABLE' : 'DESIGNED FOR YOUR NEXT GREAT FIND'}</small></div>
      <nav className="film-chapters" aria-label="Experience chapters" hidden={immersive}>{chapters.map((name, index) => <a key={name} href={`#${['discover','your-world','your-rules'][index]}`} aria-current={chapter === index ? 'location' : undefined}><span>0{index+1}</span><span>{name}</span></a>)}</nav>
      <div className="film-controls"><button className="film-control" disabled={mediaError} aria-label={paused ? 'Play background film' : 'Pause background film'} aria-pressed={!paused} onClick={() => setPaused(!paused)}>{paused ? <Play size={22}/> : <Pause size={22}/>}</button><button className="film-control" aria-label={immersive ? 'Exit cinema view' : 'Enter cinema view'} aria-pressed={immersive} onClick={() => setImmersive(!immersive)}>{immersive ? <Minimize2 size={22}/> : <Maximize2 size={22}/>}</button></div>
    </footer>
    {auth && <div className="film-auth-overlay" role="dialog" aria-modal="true" aria-label="Google account access"><div ref={dialog} className="film-auth-dialog"><button className="film-auth-close" onClick={() => setAuth(false)} aria-label="Close sign in"><X size={22}/></button><AuthPage/></div></div>}
  </div>;
}

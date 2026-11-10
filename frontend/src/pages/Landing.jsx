import { lazy, Suspense, useState, useEffect, useRef } from 'react';
import { ArrowDown, ArrowUpRight, ShieldCheck, Sparkles, SlidersHorizontal, X, Pause, Play, MoveUpRight } from 'lucide-react';
import AuthPage from './AuthPage';
import './Landing.css';
const ProductScene = lazy(() => import('../components/ProductScene'));
const editions = [{ name: 'Acid', color: '#c5f36a' }, { name: 'Glacier', color: '#b3c8ed' }, { name: 'Clay', color: '#df8d70' }];
export default function Landing({ onAuthenticated }) {
  const [auth, setAuth] = useState(false);
  const [edition, setEdition] = useState(0);
  const [paused, setPaused] = useState(false);
  const dialog = useRef(null);
  useEffect(() => {
    if (!auth) return;
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const keydown = (event) => {
      if (event.key === 'Escape') setAuth(false);
      if (event.key !== 'Tab') return;
      const items = dialog.current.querySelectorAll('button, a[href], input, select, textarea');
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', keydown);
    dialog.current?.querySelector('button')?.focus();
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown', keydown); previous?.focus(); };
  }, [auth]);
  return <div className="landing">
    <header className="landing-nav"><a className="landing-brand" href="#" aria-label="Spendwise home"><span className="brand-symbol">s<span>↗</span></span>spendwise<span className="brand-period">®</span></a><nav aria-label="Main navigation"><a href="#experience">The experience</a><a href="#how-it-works">How it works <ArrowUpRight size={12}/></a></nav><button className="landing-login" onClick={() => setAuth(true)}>Enter workspace <ArrowUpRight size={16}/></button></header>
    <main>
      <section className="landing-hero" id="experience">
        <div className="hero-grid-lines" aria-hidden="true"/>
        <div className="hero-topline"><span><i/> INTELLIGENCE MEETS INTENTION</span><span>CURATED BY AI. CONTROLLED BY YOU.</span></div>
        <div className="landing-hero-copy"><h1>GOOD FINDS.<br/><span>GREAT CONTROL.</span></h1><p>A new dimension of shopping.<br/>Discover what you love. Stay within your limits.</p><button className="landing-cta" onClick={() => setAuth(true)}>Find your next thing <ArrowUpRight size={20}/></button><span className="hero-fineprint"><ShieldCheck size={13}/> Your budget. Your rules. Always.</span></div>
        <div className="hero-object"><div className="object-aura"/><span className="object-coordinate">SW—OBJECT / 001</span><Suspense fallback={<div className="scene-fallback">Loading the next dimension…</div>}><ProductScene color={editions[edition].color} paused={paused}/></Suspense><div className="object-caption"><span>THE EVERYDAY, REIMAGINED</span><strong>Turn up the possibilities.</strong><small>Illustrative 3D concept · Drag to explore</small></div><div className="object-controls"><div className="edition-switch" aria-label="Headphone color">{editions.map((item, index) => <button key={item.name} aria-label={`${item.name} color`} aria-pressed={edition === index} className={edition === index ? 'selected' : ''} style={{ '--swatch': item.color }} onClick={() => setEdition(index)}/>)}</div><button className="motion-toggle" onClick={() => setPaused(!paused)} aria-label={paused ? 'Resume animation' : 'Pause animation'}>{paused ? <Play size={14}/> : <Pause size={14}/>}</button></div></div>
        <div className="hero-float-label"><span className="float-icon"><ShieldCheck size={18}/></span><div><strong>Impulse meets a boundary.</strong><span>Spending rules checked before checkout</span></div><span className="float-dot"/></div>
        <div className="hero-bottomline"><a href="#how-it-works"><ArrowDown size={15}/> SCROLL TO DISCOVER</a><span>SHOP SMARTER. FEEL BETTER.</span><span>01 — 03</span></div>
      </section>
      <div className="manifesto-strip"><span>LESS IMPULSE.</span><Sparkles/><span>MORE INTENTION.</span><Sparkles/><span>ALL YOU.</span><Sparkles/><span>LESS IMPULSE.</span></div>
      <section className="landing-process" id="how-it-works"><div className="process-heading"><span className="landing-kicker">01 / A BETTER WAY TO BUY</span><h2>Big possibilities.<br/><span>Thoughtful decisions.</span></h2><p>From “I need” to “that’s the one.”<br/>A little intelligence at every step.</p></div><div className="process-cards">{[{icon: Sparkles, title:'Find your kind of thing.', copy:'Tell the assistant what you’re looking for. Explore real products matched to your intent.', tag:'01 / DISCOVER'}, {icon: SlidersHorizontal, title:'Set your own boundaries.', copy:'Define your spending limits. Your policy checks every purchase before checkout.', tag:'02 / PROTECT'}, {icon: ShieldCheck, title:'Make the final call.', copy:'Review the details and approve a clearly labelled PayPal Sandbox payment.', tag:'03 / APPROVE'}].map(({icon: Icon, title, copy, tag}) => <article key={tag}><div className="process-card-top"><Icon size={24}/><span>{tag}</span></div><h3>{title}</h3><p>{copy}</p><button onClick={() => setAuth(true)} aria-label={`Get started: ${title}`}><MoveUpRight size={21}/></button></article>)}</div></section>
      <section className="landing-closing"><span className="landing-kicker">YOUR NEXT CHAPTER STARTS HERE</span><h2>Want it.<br/><em>Spend wisely.</em></h2><button className="landing-cta" onClick={() => setAuth(true)}>Enter your workspace <ArrowUpRight size={20}/></button><p>Product discovery and Sandbox payments are separate.<br/>A Sandbox payment does not purchase from the retailer.</p></section>
    </main><footer className="landing-footer"><a href="#" className="landing-brand">spendwise®</a><span>AI recommends. You decide.</span><a href="#experience">BACK TO TOP ↑</a></footer>
    {auth && <div className="auth-overlay" role="dialog" aria-modal="true" aria-label="Sign in to Spendwise" onKeyDown={event => { if(event.key === 'Escape') setAuth(false); }}><div className="auth-dialog" ref={dialog}><button autoFocus className="auth-close" onClick={() => setAuth(false)} aria-label="Close sign in"><X size={22}/></button><AuthPage onAuthenticated={onAuthenticated}/></div></div>}
  </div>;
}


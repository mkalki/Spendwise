import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowDown, ArrowUpRight, Check, ChevronRight, Pause, Play, RotateCcw, RefreshCw, ShieldCheck, SlidersHorizontal, Wallet, ReceiptText } from 'lucide-react';
import { api } from '../services/api';
import { useReducedMotion } from '../components/useReducedMotion';
import MotionBackdrop from '../components/MotionBackdrop';
import './Dashboard.css';

const ProductScene = lazy(() => import('../components/ProductScene'));
const finishes = [{ name: 'Chrome', color: '#d8dce6' }, { name: 'Cobalt', color: '#3158c9' }, { name: 'Crimson', color: '#b31839' }];
const story = [
  { kicker: '01 / YOUR PERSONAL BUYING UNIVERSE', title: <>GOOD TASTE.<br/><em>GREAT LIMITS.</em></>, copy: 'Discover what moves you. Keep every decision within your own boundaries.' },
  { kicker: '02 / A DIFFERENT PERSPECTIVE', title: <>SEE EVERY<br/><em>POSSIBILITY.</em></>, copy: 'A little intelligence. A lot of intention. Your next great find starts with a conversation.' },
  { kicker: '03 / ALWAYS IN YOUR CONTROL', title: <>YOUR WORLD.<br/><em>YOUR RULES.</em></>, copy: 'AI discovers. Your policy checks. You approve. Explore freely, spend thoughtfully.' },
];

function DashboardSurface({ children, className = '', ...props }) {
  return <div className={`studio-surface ${className}`} {...props}>{children}</div>;
}

function BlockedHistoryList({ title, entries, empty }) {
  return <div>
    <h4>{title}</h4>
    {entries.length ? <ul className="studio-blocked-list">
      {entries.map(item => <li key={item.id}>
        <div>
          <strong>{item.productName}</strong>
          <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString()}</time>
        </div>
        {item.amount != null && item.currency && <span>{item.currency} {item.amount}</span>}
        <p>{item.reason || 'The offer did not pass the eligibility checks.'}</p>
      </li>)}
    </ul> : <p>{empty}</p>}
  </div>;
}

export default function Dashboard({ user }) {
  const navigate = useNavigate();
  const track = useRef(null);
  const rotationLabel = useRef(null);
  const reduced = useReducedMotion();
  const [policy, setPolicy] = useState(null);
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [blockedHistory, setBlockedHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [errors, setErrors] = useState([]);
  const [stage, setStage] = useState(0);
  const [finish, setFinish] = useState(0);
  const [autoFinish, setAutoFinish] = useState(true);
  const [crispFilm, setCrispFilm] = useState(false);
  const [paused, setPaused] = useState(false);
  const [resetKey, setResetKey] = useState(0);
  const [purchase, setPurchase] = useState(0);
  const [ringMode, setRingMode] = useState('remaining');

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      api.getPolicy(), api.getTransactionSummary(), api.getTransactions(), api.getBlockedPurchases(),
    ]).then(results => {
      if (!active) return;
      const [p, s, h, blocked] = results;
      setPolicy(p.status === 'fulfilled' ? p.value : null);
      setSummary(s.status === 'fulfilled' ? s.value : null);
      setTransactions(h.status === 'fulfilled' ? (h.value.transactions || []).slice(0, 5) : []);
      setBlockedHistory(blocked.status === 'fulfilled' ? blocked.value : null);
      setErrors([p, s, h].filter(result => result.status === 'rejected' && result.reason.status !== 404).map(result => result.reason.message || 'Unable to load account data.'));
      setLoading(false);
    });
    return () => { active = false; };
  }, [revision]);

  useEffect(() => {
    let frame;
    function update() {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const rect = track.current.getBoundingClientRect();
        const progress = Math.min(1, Math.max(0, -rect.top / Math.max(rect.height - window.innerHeight, 1)));
        setStage(Math.min(2, Math.floor(progress * 3)));
      });
    }
    window.addEventListener('scroll', update, { passive: true }); update();
    return () => { window.removeEventListener('scroll', update); cancelAnimationFrame(frame); };
  }, []);

  function jumpToStage(index) {
    const rect = track.current.getBoundingClientRect();
    const distance = Math.max(rect.height - window.innerHeight, 1);
    window.scrollTo({ top: window.scrollY + rect.top + distance * (index === 0 ? 0 : index === 1 ? .5 : .98), behavior: reduced ? 'instant' : 'smooth' });
  }
  const currency = policy?.currency || summary?.currency || 'USD';
  const money = amount => new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(amount) || 0);
  const spent = Number(summary?.completedSpend || 0);
  const budget = Number(policy?.weeklySpendingLimit || 0);
  const remaining = Number(summary?.remainingBudget || 0);
  const usage = budget ? Math.min(100, Math.max(0, spent / budget * 100)) : 0;
  const remainingAfter = Math.max(0, remaining - purchase);
  const withinBudget = purchase <= remaining;
  const perItemAllowed = !policy || purchase <= Number(policy.maxItemPrice);
  const name = (user?.displayName || user?.name)?.split(' ')[0] || user?.username || 'explorer';
  function refresh() { setLoading(true); setRevision(value => value + 1); }

  return <div className="studio-dashboard">
    <MotionBackdrop paused={paused} reduced={reduced} crisp={crispFilm}/>
    <section ref={track} className="studio-scroll-track" aria-label="Scroll-driven 3D shopping experience">
      <div className="studio-pinned-scene">
        <div className="studio-scene-grid" aria-hidden="true"/>
        <div className="studio-scene-wall" aria-hidden="true">{Array.from({length:12}, (_, i) => <div key={i}><img src={`/media/product-00${i % 3 + 1}.jpg`} alt=""/><span>SW—OBJECT / {String(i + 1).padStart(3,'0')}</span></div>)}</div>
        <div className="studio-scene-topline"><span><i/> WELCOME BACK, {name.toUpperCase()}</span><span>PRIVATE WORKSPACE / VOL. 001</span></div>
        <div className="studio-live-model"><Suspense fallback={<div className="studio-scene-loading"><span className="studio-loading-orbit"/>INITIALIZING 3D SCENE</div>}><ProductScene cinematic scrollTarget={track} rotationLabel={rotationLabel} color={finishes[finish].color} autoFinish={autoFinish} paused={paused || reduced} resetKey={resetKey}/></Suspense></div>
        <div className="studio-story-copy" aria-live="polite"><span className="studio-eyebrow">{story[stage].kicker}</span><h2 key={stage}>{story[stage].title}</h2><p>{story[stage].copy}</p><button className="studio-button" onClick={() => navigate('/assistant')}>FIND YOUR NEXT THING <ArrowUpRight size={18}/></button></div>
        <div className="studio-scene-caption"><span>SW / SOUND OBJECT</span><strong>Intention, in every detail.</strong><small>Illustrative concept · Drag to inspect</small></div>
        <div className="studio-scene-controls"><div className="studio-finish-picker" role="group" aria-label="Model finish">{finishes.map((item,index) => <button key={item.name} onClick={() => { setFinish(index); setAutoFinish(false); }} aria-label={`${item.name} finish`} aria-pressed={!autoFinish && finish === index} style={{'--finish':item.color}}>{finish === index && <Check size={10}/>}</button>)}<span>{autoFinish ? `Auto / ${finishes[stage].name}` : finishes[finish].name}</span></div><button className="studio-auto-finish" aria-label="Automatic scroll colors" aria-pressed={autoFinish} onClick={() => setAutoFinish(value => !value)}>AUTO</button><button className="studio-auto-finish" aria-label="Clear background film" aria-pressed={crispFilm} onClick={() => setCrispFilm(value => !value)}>{crispFilm ? 'CLEAR' : 'BLUR'}</button><button className="studio-icon-button" aria-label={paused ? 'Resume scene motion' : 'Pause scene motion'} aria-pressed={paused} onClick={() => setPaused(!paused)}>{paused ? <Play size={16}/> : <Pause size={16}/>}</button><button className="studio-icon-button" aria-label="Reset model view" onClick={() => setResetKey(value => value + 1)}><RotateCcw size={16}/></button></div>
        <div className="studio-hero-bottom"><nav aria-label="3D scene chapters">{['DISCOVER','EXPLORE','CONTROL'].map((label,index) => <button key={label} aria-current={stage === index ? 'step' : undefined} onClick={() => jumpToStage(index)}><small>0{index+1}</small>{label}</button>)}</nav><span className="studio-rotation"><output ref={rotationLabel} aria-label="Model rotation" aria-live="off">000°</output> / 360°</span><a href="#spending-overview">SCROLL TO ROTATE + RECOLOR <ArrowDown size={13}/></a></div>
      </div>
    </section>

    <section className="studio-overview" id="spending-overview" aria-busy={loading}>
      <header className="studio-section-header"><div><span className="studio-eyebrow">01 / THE BIG PICTURE</span><h2>YOUR MONEY.<br/><em>IN PERSPECTIVE.</em></h2></div><div className="studio-data-tools"><span className="studio-data-status"><i/>{loading ? 'SYNCING ACCOUNT' : errors.length ? 'SOME DATA UNAVAILABLE' : 'CONNECTED TO YOUR ACCOUNT'}</span><button className="studio-icon-button" onClick={refresh} disabled={loading} aria-label="Refresh account data"><RefreshCw size={16} className={loading ? 'studio-spinning' : ''}/></button></div></header>
      {errors.length > 0 && <div className="policy-message error-message" role="alert">Some account information could not be loaded. {errors[0]} <button className="text-button" onClick={refresh}>Retry</button></div>}
      <div className="studio-stats">{[{label:'SPENT THIS WEEK',value:money(spent),note:'Completed payments',Icon:Wallet},{label:'STILL YOURS TO SPEND',value:money(remaining),note:'Within your weekly policy',Icon:ShieldCheck},{label:'COMPLETED ORDERS',value:String(summary?.completedOrders || 0),note:'Your protected shopping activity',Icon:ReceiptText}].map(({label,value,note,Icon}, index) => <DashboardSurface key={label} className="studio-stat"><div><span>{label}</span><Icon size={18}/></div><strong>{loading ? '—' : value}</strong><p><span>0{index+1}</span>{note}</p></DashboardSurface>)}</div>
      <div className="studio-budget-layout">
        <DashboardSurface className="studio-budget"><div className="studio-card-title"><span>YOUR WEEKLY ORBIT</span><Wallet size={19}/></div><div className="studio-budget-visual"><button className="studio-budget-orbit" aria-label={`Show ${ringMode === 'remaining' ? 'spent' : 'remaining'} budget`} onClick={() => setRingMode(ringMode === 'remaining' ? 'spent' : 'remaining')} style={{'--usage':`${usage * 3.6}deg`}}><span className="studio-budget-orbit-inner"><small>{ringMode === 'remaining' ? 'REMAINING' : 'SPENT'}</small><strong>{loading ? '—' : money(ringMode === 'remaining' ? remaining : spent)}</strong><span>{loading ? 'Syncing…' : `${Math.round(usage)}% of budget used`}</span></span></button><div className="studio-budget-legend"><button className={ringMode === 'spent' ? 'selected' : ''} onClick={() => setRingMode('spent')}><i/>Spent <strong>{loading ? '—' : money(spent)}</strong></button><button className={ringMode === 'remaining' ? 'selected' : ''} onClick={() => setRingMode('remaining')}><i/>Remaining <strong>{loading ? '—' : money(remaining)}</strong></button><div>Weekly limit <strong>{policy ? money(budget) : 'Not configured'}</strong></div></div></div><button className="studio-text-link" onClick={() => navigate('/policies')}>SET YOUR BOUNDARIES <ArrowUpRight size={15}/></button></DashboardSurface>
        <DashboardSurface className="studio-simulator"><div className="studio-card-title"><span>A LITTLE WHAT-IF</span><SlidersHorizontal size={19}/></div><h3>Room for one more?</h3><p>Move the slider to preview a purchase against your current budget.</p><div className="studio-preview-amount"><label htmlFor="purchase-preview">PURCHASE PREVIEW</label><output htmlFor="purchase-preview">{money(purchase)}</output></div><input id="purchase-preview" type="range" min="0" max={Math.max(budget * 1.25,remaining * 1.25,100)} step="1" value={purchase} onChange={event => setPurchase(Number(event.target.value))} disabled={!policy || loading}/><div className={`studio-preview-result ${!withinBudget || !perItemAllowed ? 'over-limit' : ''}`} role="status">{!policy ? <><ShieldCheck size={17}/><span>Set a spending policy to explore your budget.</span></> : <><ShieldCheck size={17}/><span>{withinBudget && perItemAllowed ? `${money(remainingAfter)} would remain in your weekly budget.` : !withinBudget ? 'This amount exceeds your remaining weekly budget.' : 'This amount exceeds your per-item price limit.'}</span></>}</div><small className="studio-preview-note">Preview only. Final eligibility also checks order limits and other rules. Your policy and balance are unchanged.</small></DashboardSurface>
      </div>
    </section>

    <section className="studio-discover"><div className="studio-discover-copy"><span className="studio-eyebrow">02 / A LITTLE INTELLIGENCE GOES A LONG WAY</span><h2>WHAT’S ON<br/><em>YOUR MIND?</em></h2><p>Start with an idea. Let your assistant handle the search.</p></div><div className="studio-prompts">{['Find headphones within my budget','Help me find everyday essentials','Show me a thoughtful gift'].map((query,index) => <button key={query} onClick={() => navigate(`/assistant?query=${encodeURIComponent(query)}`)}><span>0{index+1}</span><strong>{query}</strong><ArrowUpRight size={22}/></button>)}</div></section>

    <section className="studio-activity"><header className="studio-section-header"><div><span className="studio-eyebrow">03 / YOUR TRAIL</span><h2>RECENT <em>MOVES.</em></h2></div><button className="studio-text-link" onClick={() => navigate('/transactions')}>VIEW ALL ACTIVITY <ArrowUpRight size={16}/></button></header><div className="studio-activity-panel"><div className="studio-activity-labels"><span>THE FIND</span><span>WHEN</span><span>AMOUNT</span><span>STATUS</span></div>{loading ? <p className="studio-activity-empty">Loading your activity…</p> : transactions.length ? transactions.map((item,index) => <div className="studio-activity-row" key={item.checkoutSessionId || index}><strong>{item.productName || 'Shopping payment'}</strong><span>{new Date(item.createdAt).toLocaleDateString()}</span><strong>{new Intl.NumberFormat('en-US',{style:'currency',currency:item.currency || currency}).format(Number(item.amount)||0)}</strong><span className={`studio-transaction-status ${item.status === 'CAPTURED' ? 'captured' : ''}`}>{item.status}</span></div>) : <div className="studio-activity-empty"><ReceiptText size={28}/><h3>A fresh start.</h3><p>Your shopping activity will appear here.</p><button className="studio-text-link" onClick={() => navigate('/assistant')}>DISCOVER YOUR FIRST FIND <ChevronRight size={15}/></button></div>}</div></section>
    <section className="studio-activity studio-blocked-history" aria-labelledby="blocked-history-heading" aria-busy={loading}>
      <div className="studio-activity-panel">
        <h3 id="blocked-history-heading">Blocked purchase history</h3>
        <p>See why recent checkouts were stopped and which offers were filtered out during searches.</p>
        {loading ? <p role="status">Loading blocked history…</p> : blockedHistory ? (
          <div className="studio-blocked-columns">
            <BlockedHistoryList title="Blocked checkouts" entries={blockedHistory.checkoutAttempts}
              empty="No blocked checkout attempts recorded." />
            <BlockedHistoryList title="Offers filtered out in search" entries={blockedHistory.rejectedOffers}
              empty="No rejected offers found in your search history." />
          </div>
        ) : <div role="alert">Blocked purchase history could not be loaded. <button className="text-button" onClick={refresh}>Retry</button></div>}
      </div>
    </section>
    <footer className="studio-dashboard-footer"><span>SPENDWISE / YOUR PERSONAL BUYING UNIVERSE</span><span>AI RECOMMENDS. YOU DECIDE.</span><a href="#top">BACK TO TOP ↑</a></footer>
  </div>;
}

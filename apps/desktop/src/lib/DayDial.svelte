<script lang="ts">
  import { app } from "./state.svelte";
  import type { Day, TimeKey } from "./types";

  let { day }: { day: Day } = $props();

  // 270° arc from Fajr (bottom-start) to Isha (bottom-end), clockwise in LTR.
  const R = 120, CX = 150, CY = 150, START = 135, SWEEP = 270;
  const keys: TimeKey[] = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"];

  const t0 = $derived(day.times.fajr ?? 0);
  const t1 = $derived(day.times.isha ?? t0 + 1);
  const frac = (t: number) => Math.min(1, Math.max(0, (t - t0) / (t1 - t0)));
  const rtl = $derived(app.dir === "rtl");

  function pt(f: number, r = R) {
    let a = START + f * SWEEP;
    if (rtl) a = 180 - a; // mirror horizontally
    const rad = (a * Math.PI) / 180;
    return { x: CX + r * Math.cos(rad), y: CY + r * Math.sin(rad) };
  }
  function arc(f0: number, f1: number) {
    const a = pt(f0), b = pt(f1);
    const large = (f1 - f0) * SWEEP > 180 ? 1 : 0;
    return `M ${a.x} ${a.y} A ${R} ${R} 0 ${large} ${rtl ? 0 : 1} ${b.x} ${b.y}`;
  }

  const marks = $derived(
    keys.filter((k) => day.times[k] !== null).map((k) => ({ k, f: frac(day.times[k]!), at: day.times[k]! })),
  );
  const nowF = $derived(frac(app.now));
  const nx = $derived(app.next);
  const cur = $derived(app.current);
  // Highlight the fasila: from the current prayer (or now) to the next prayer.
  const gap = $derived.by(() => {
    if (!nx || nx.day.date !== day.date) return null;
    const from = cur && cur.at >= t0 ? frac(cur.at) : 0;
    return { from, to: frac(nx.at) };
  });
  const remaining = $derived(nx ? nx.at - app.now : 0);
  const total = $derived(nx && cur ? nx.at - cur.at : 1);
</script>

<figure class="dial" aria-label={nx ? `${app.keyName(nx.key, nx.day)} ${app.t.in} ${app.fmt.countdown(remaining)}` : ""}>
  <svg viewBox="0 0 300 300" role="img">
    <defs>
      <linearGradient id="gapg" x1="0" x2="1">
        <stop offset="0" stop-color="var(--accent)" stop-opacity="0.55" />
        <stop offset="1" stop-color="var(--accent)" />
      </linearGradient>
    </defs>
    <path d={arc(0, 1)} class="track" />
    {#if gap}
      <path d={arc(gap.from, gap.to)} class="gap" stroke="url(#gapg)" />
      <path d={arc(gap.from, Math.max(gap.from, Math.min(nowF, gap.to)))} class="elapsed" />
    {/if}
    {#each marks as m}
      {@const p = pt(m.f)}
      {@const lp = pt(m.f, R + 26)}
      <circle cx={p.x} cy={p.y} r={m.k === "sunrise" ? 3.5 : 6} class="mark" class:next={nx?.key === m.k && nx.day.date === day.date} class:sunrise={m.k === "sunrise"} />
      {#if m.k !== "sunrise"}
        <text x={lp.x} y={lp.y} class="lbl" text-anchor="middle" dominant-baseline="middle">{app.keyName(m.k, day)}</text>
      {/if}
    {/each}
    {#if app.now >= t0 && app.now <= t1}
      {@const s = pt(nowF)}
      <circle cx={s.x} cy={s.y} r="11" class="sun-halo" />
      <circle cx={s.x} cy={s.y} r="6.5" class="sun" />
    {/if}
  </svg>
  {#if nx}
    <figcaption>
      <div class="eyebrow">{app.t.next}</div>
      <div class="name">{app.keyName(nx.key, nx.day)}</div>
      <div class="count tabular" aria-live="off">{app.fmt.countdown(remaining)}</div>
      <div class="at muted tabular">{app.t.at} {app.fmt.time(nx.at)}</div>
      {#if cur}
        <div class="bar" aria-hidden="true"><span style:width="{Math.min(100, (1 - remaining / total) * 100)}%"></span></div>
      {/if}
    </figcaption>
  {/if}
</figure>

<style>
  .dial { position: relative; margin: 0; width: 100%; max-width: 420px; aspect-ratio: 1; margin-inline: auto; }
  svg { width: 100%; height: 100%; overflow: visible; }
  .track { fill: none; stroke: var(--ink-3); stroke-opacity: 0.45; stroke-width: 10; stroke-linecap: round; }
  .gap { fill: none; stroke-width: 10; stroke-linecap: round; filter: drop-shadow(0 0 8px var(--accent)); }
  .elapsed { fill: none; stroke: var(--ink); stroke-opacity: 0.35; stroke-width: 4; stroke-linecap: round; }
  .mark { fill: var(--sky-top); stroke: var(--ink); stroke-width: 2.5; }
  .mark.sunrise { fill: var(--ink-2); stroke: none; }
  .mark.next { fill: var(--accent); stroke: var(--ink); }
  .lbl { font-size: 11.5px; fill: var(--ink-2); font-weight: 500; }
  .sun { fill: #fff6d6; }
  .sun-halo { fill: var(--accent); opacity: 0.35; animation: pulse 3s ease-in-out infinite; transform-box: fill-box; transform-origin: center; }
  :global(:root[data-motion="reduce"]) .sun-halo { animation: none; }
  @keyframes pulse { 50% { transform: scale(1.5); opacity: 0.12; } }
  figcaption { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; gap: 2px; padding-top: 8px; }
  .eyebrow { font-size: 12px; text-transform: uppercase; letter-spacing: 0.14em; color: var(--ink-2); }
  :global(:root[lang="ar"]) .eyebrow { letter-spacing: 0; text-transform: none; font-size: 14px; }
  .name { font-size: 30px; font-weight: 600; }
  .count { font-size: clamp(40px, 6vw, 60px); font-weight: 300; line-height: 1.05; letter-spacing: -0.02em; }
  .at { font-size: 15px; }
  .bar { width: 120px; height: 4px; border-radius: 4px; background: var(--glass-strong); margin: 10px auto 0; overflow: hidden; }
  .bar span { display: block; height: 100%; background: var(--accent); border-radius: 4px; }
</style>

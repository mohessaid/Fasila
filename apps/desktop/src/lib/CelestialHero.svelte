<script lang="ts">
  import { app } from "./state.svelte";
  import type { Day, TimeKey } from "./types";

  let { day }: { day: Day } = $props();

  // Scene geometry (viewBox units). The sun travels an arc from sunrise (start) to Maghrib (end).
  const W = 600, H = 268, INSET = 24, ARC_W = W - 2 * INSET, ARC_BOTTOM = 204, ARC_H = 144, BODY = 92;
  const rtl = $derived(app.dir === "rtl");

  function arcPoint(p: number) {
    const a = Math.PI - p * Math.PI;
    const x = INSET + ARC_W / 2 + Math.cos(a) * (ARC_W / 2);
    const y = ARC_BOTTOM - Math.sin(a) * ARC_H;
    return { x: rtl ? W - x : x, y };
  }
  const track = $derived(
    Array.from({ length: 49 }, (_, i) => arcPoint(i / 48))
      .map((p, i) => `${i ? "L" : "M"} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
      .join(" "),
  );

  const sky = $derived(app.skyState);
  const sun = $derived(arcPoint(sky.sun));
  const sr = $derived(day.times.sunrise), mg = $derived(day.times.maghrib);
  const markers = $derived.by(() => {
    if (!sr || !mg) return [];
    return (["sunrise", "dhuhr", "asr", "maghrib"] as TimeKey[])
      .filter((k) => day.times[k] !== null)
      .map((k) => ({ k, ...arcPoint((day.times[k]! - sr) / (mg - sr)) }));
  });
  const nx = $derived(app.next);

  // Mountains (two layers), fading into the sky.
  const far = `M 0 268 L 0 238 L ${0.1 * W} 214 L ${0.18 * W} 228 L ${0.3 * W} 192 L ${0.43 * W} 226 L ${0.55 * W} 204 L ${0.68 * W} 231 L ${0.8 * W} 200 L ${W} 226 L ${W} 268 Z`;
  const near = `M 0 268 L 0 252 L ${0.11 * W} 236 L ${0.21 * W} 246 L ${0.33 * W} 218 L ${0.45 * W} 247 L ${0.56 * W} 224 L ${0.68 * W} 248 L ${0.8 * W} 214 L ${W} 239 L ${W} 268 Z`;

  // Moon: lit region for phase 0..1 (0 new, 0.5 full). Mirrored in the southern hemisphere.
  const MR = 0.28 * BODY, MX = W / 2, MY = 72 + BODY / 2;
  const phase = $derived(app.moon?.phase ?? 0.5);
  const litPath = $derived.by(() => {
    const h = phase, r = MR, cx = MX, cy = MY;
    if (h < 0.005 || h > 0.995) return "";
    if (Math.abs(h - 0.5) < 0.005) return `M ${cx} ${cy - r} A ${r} ${r} 0 0 1 ${cx} ${cy + r} A ${r} ${r} 0 0 1 ${cx} ${cy - r}`;
    if (h < 0.5) {
      const crescent = h < 0.25;
      const rx = crescent ? r * (1 - h / 0.25) : r * ((h - 0.25) / 0.25);
      return `M ${cx} ${cy - r} A ${r} ${r} 0 0 1 ${cx} ${cy + r} A ${rx} ${r} 0 0 ${crescent ? 0 : 1} ${cx} ${cy - r}`;
    }
    const e = h - 0.5, gibbous = e < 0.25;
    const rx = gibbous ? r * (1 - e / 0.25) : r * ((e - 0.25) / 0.25);
    return `M ${cx} ${cy - r} A ${r} ${r} 0 0 0 ${cx} ${cy + r} A ${rx} ${r} 0 0 ${gibbous ? 0 : 1} ${cx} ${cy - r}`;
  });
  const south = $derived((app.config?.location.latitude ?? 0) < 0);

  // After Maghrib the Hijri day has begun: show tomorrow's Hijri day at night.
  const hijriDay = $derived.by(() => {
    const t = app.days[1], n = app.days[2];
    if (t?.times.maghrib && app.now >= t.times.maghrib && n) return n.hijri.day;
    if (t?.times.fajr && app.now < t.times.fajr) return t.hijri.day;
    return t?.hijri.day ?? 1;
  });
  const whiteDays = $derived(hijriDay >= 13 && hijriDay <= 15);
</script>

<div class="hero">
  <svg viewBox="0 0 {W} {H}" class="scene" aria-hidden="true">
    <defs>
      <linearGradient id="farMountains" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#F8FAFC" stop-opacity={sky.isDaytime ? 0.12 : 0.2} />
        <stop offset="68%" stop-color="#F8FAFC" stop-opacity={sky.isDaytime ? 0.04 : 0.06} />
        <stop offset="80%" stop-color="#F8FAFC" stop-opacity="0" />
      </linearGradient>
      <linearGradient id="nearMountains" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#F8FAFC" stop-opacity={sky.isDaytime ? 0.18 : 0.26} />
        <stop offset="68%" stop-color="#F8FAFC" stop-opacity={sky.isDaytime ? 0.05 : 0.07} />
        <stop offset="80%" stop-color="#F8FAFC" stop-opacity="0" />
      </linearGradient>
      <radialGradient id="sunAtmosphere">
        <stop offset="0%" stop-color="#F8FAFC" stop-opacity="0.3" />
        <stop offset="32%" stop-color="var(--accent)" stop-opacity="0.18" />
        <stop offset="72%" stop-color="var(--accent)" stop-opacity="0.05" />
        <stop offset="100%" stop-color="var(--accent)" stop-opacity="0" />
      </radialGradient>
      <radialGradient id="sunDisc" cx="32%" cy="28%" r="76%">
        <stop offset="0%" stop-color="#F8FAFC" />
        <stop offset="44%" stop-color="#F8FAFC" stop-opacity="0.96" />
        <stop offset="78%" stop-color="var(--accent)" stop-opacity="0.96" />
        <stop offset="100%" stop-color="var(--accent)" />
      </radialGradient>
      <radialGradient id="moonGlow">
        <stop offset="0%" stop-color="#F8FAFC" stop-opacity="0.32" />
        <stop offset="45%" stop-color="#CBD5E1" stop-opacity="0.12" />
        <stop offset="100%" stop-color="#CBD5E1" stop-opacity="0" />
      </radialGradient>
      <linearGradient id="moonSurface" x1="20%" y1="15%" x2="80%" y2="90%">
        <stop offset="0%" stop-color="#F8FAFC" />
        <stop offset="58%" stop-color="#E2E8F0" />
        <stop offset="100%" stop-color="#CBD5E1" />
      </linearGradient>
    </defs>

    <!-- The sun's path and today's daytime prayers on it -->
    <path d={track} class="track" class:faint={!sky.isDaytime} />
    {#each markers as m}
      <circle cx={m.x} cy={m.y} r={m.k === "sunrise" || m.k === "maghrib" ? 2.5 : 3.5}
        class="mark" class:next={nx?.key === m.k && nx.day.date === day.date} />
      {#if m.k === "dhuhr" || m.k === "asr"}
        <text x={m.x} y={m.y - 14} text-anchor="middle" class="lbl">{app.keyName(m.k, day)}</text>
      {/if}
    {/each}

    <path d={far} fill="url(#farMountains)" />
    <path d={near} fill="url(#nearMountains)" />

    <!-- Sun -->
    <g class="body" class:hidden={!sky.isDaytime} style:transform="translate({sun.x}px, {sun.y}px)">
      <circle r={0.49 * BODY} fill="url(#sunAtmosphere)" class="sun-glow" />
      <circle r={0.235 * BODY} fill="var(--accent)" />
      <circle r={0.235 * BODY} fill="url(#sunDisc)" />
      <circle r={0.235 * BODY} fill="none" stroke="#F8FAFC" stroke-opacity="0.2" stroke-width="0.8" />
    </g>

    <!-- Moon, in today's real phase -->
    <g class="body moon" class:hidden={sky.isDaytime}>
      <g transform={south ? `translate(${2 * MX} 0) scale(-1 1)` : undefined}>
        <circle cx={MX} cy={MY} r={0.4 * BODY} fill="url(#moonGlow)" />
        <circle cx={MX} cy={MY} r={MR} fill="#060A14" opacity="0.72" />
        <path d={litPath} fill="url(#moonSurface)" />
        <g opacity="0.16" fill="#CBD5E1">
          <circle cx={MX - 0.36 * MR} cy={MY - 0.22 * MR} r={0.11 * MR} />
          <circle cx={MX + 0.2 * MR} cy={MY + 0.28 * MR} r={0.08 * MR} />
          <circle cx={MX + 0.38 * MR} cy={MY - 0.15 * MR} r={0.055 * MR} />
        </g>
      </g>
    </g>
  </svg>

  {#if nx}
    <div class="text">
      <div class="next">{app.t.isNext.replace("{p}", app.keyName(nx.key, nx.day))}</div>
      <div class="count tabular">{app.fmt.countdown(nx.at - app.now)}</div>
      <div class="at muted tabular">{app.t.at} {app.fmt.time(nx.at)}</div>
      {#if !sky.isDaytime}
        <div class="phase">
          {whiteDays ? app.t.whiteDaysTitle : `${app.t.lunarDay} ${app.fmt.num(hijriDay)}`}
          {#if app.moon}<span class="faint"> · {app.fmt.num(Math.round(app.moon.illumination * 100))}% {app.t.lit}</span>{/if}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .hero { display: grid; justify-items: center; }
  .scene { width: 100%; max-width: 640px; height: auto; display: block; overflow: visible; }
  .track { fill: none; stroke: #f8fafc; stroke-opacity: 0.22; stroke-width: 1.2; stroke-dasharray: 2 6; stroke-linecap: round; }
  .track.faint { stroke-opacity: 0.08; }
  .mark { fill: #f8fafc; fill-opacity: 0.55; }
  .mark.next { fill: var(--accent); fill-opacity: 1; }
  .lbl { font-size: 12px; fill: var(--ink-2); font-weight: 500; }
  .body { transition: opacity 1.2s ease, transform 1.2s cubic-bezier(0.2, 0.8, 0.2, 1); }
  .body.hidden { opacity: 0; }
  .moon { transform-box: fill-box; transform-origin: center; animation: shimmer 3.5s ease-in-out infinite alternate; }
  .moon.hidden { animation: none; }
  .sun-glow { transform-box: fill-box; transform-origin: center; animation: glow 2.5s ease-in-out infinite alternate; }
  @keyframes glow { from { opacity: 0.4; transform: scale(1.06); } to { opacity: 0.8; transform: scale(1.12); } }
  @keyframes shimmer { from { opacity: 0.78; } to { opacity: 1; } }
  :global(:root[data-motion="reduce"]) .moon, :global(:root[data-motion="reduce"]) .sun-glow { animation: none; }
  .text { display: grid; justify-items: center; gap: 2px; margin-top: -6px; text-align: center; }
  .next { font-size: 24px; font-weight: 600; letter-spacing: -0.5px; }
  :global(:root[lang="ar"]) .next { letter-spacing: 0; }
  .count { font-size: clamp(44px, 6vw, 64px); font-weight: 300; line-height: 1.05; letter-spacing: -0.02em; }
  .at { font-size: 15px; }
  .phase { margin-top: 8px; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 2px; color: var(--accent); }
  :global(:root[lang="ar"]) .phase { letter-spacing: 0; font-size: 14px; }
</style>

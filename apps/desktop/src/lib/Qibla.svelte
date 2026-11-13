<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";

  let q = $state<{ bearing: number; distance_km: number } | null>(null);
  $effect(() => {
    void app.config;
    api.qibla().then((r) => (q = r));
  });
  const ticks = Array.from({ length: 72 }, (_, i) => i * 5);
  const cardinals = [["N", 0], ["E", 90], ["S", 180], ["W", 270]] as const;
</script>

<div class="wrap">
  <section class="glass card qibla">
    <h2>{app.t.qiblaTitle}</h2>
    {#if q}
      <svg viewBox="0 0 300 300" class="rose" role="img" aria-label={`${q.bearing.toFixed(1)}° ${app.t.fromNorth}`}>
        <circle cx="150" cy="150" r="128" class="ring" />
        {#each ticks as t}
          <line x1="150" y1="26" x2="150" y2={t % 90 === 0 ? 40 : t % 30 === 0 ? 36 : 32} transform="rotate({t} 150 150)" class="tick" class:major={t % 90 === 0} />
        {/each}
        {#each cardinals as [c, a]}
          <text x="150" y="58" transform="rotate({a} 150 150)" text-anchor="middle" class="card-lbl" class:north={c === "N"}>{c}</text>
        {/each}
        <g transform="rotate({q.bearing} 150 150)" class="needle">
          <path d="M150 44 L162 150 L150 140 L138 150 Z" class="head" />
          <path d="M150 256 L158 150 L150 158 L142 150 Z" class="tail" />
          <rect x="140" y="20" width="20" height="20" rx="3" class="kaaba" />
        </g>
        <circle cx="150" cy="150" r="6" class="hub" />
      </svg>
      <div class="readout">
        <div class="deg tabular">{app.fmt.num(Math.round(q.bearing * 10) / 10)}°</div>
        <div class="muted">{app.t.fromNorth}</div>
        <div class="muted">{app.t.distance}: {app.fmt.num(Math.round(q.distance_km))} km</div>
      </div>
      <p class="faint help">{app.t.qiblaHelp}</p>
    {/if}
  </section>
</div>

<style>
  .wrap { display: grid; place-items: center; }
  .qibla { width: min(520px, 100%); display: grid; justify-items: center; gap: 8px; text-align: center; }
  .rose { width: min(360px, 80vw); }
  .ring { fill: var(--glass); stroke: var(--glass-border); stroke-width: 1.5; }
  .tick { stroke: var(--ink-3); stroke-width: 1.2; }
  .tick.major { stroke: var(--ink); stroke-width: 2; }
  .card-lbl { fill: var(--ink-2); font-size: 14px; font-weight: 600; }
  .card-lbl.north { fill: var(--accent); }
  :global(:root[data-light="true"]) .card-lbl.north { fill: #b3261e; }
  .needle .head { fill: var(--accent); filter: drop-shadow(0 0 6px var(--accent)); }
  .needle .tail { fill: var(--ink-3); }
  .kaaba { fill: #141414; stroke: #d4af37; stroke-width: 2; }
  .hub { fill: var(--ink); }
  .needle { transition: transform 1.2s cubic-bezier(0.2, 0.8, 0.2, 1); }
  .deg { font-size: 44px; font-weight: 300; }
  .help { max-width: 360px; font-size: 13px; }
</style>

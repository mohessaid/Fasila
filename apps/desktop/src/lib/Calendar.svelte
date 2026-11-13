<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import Icon from "./Icon.svelte";
  import type { Day, Occurrence, TimeKey } from "./types";

  let month = $state(app.today.slice(0, 7)); // YYYY-MM
  let days = $state<Day[]>([]);
  let selected = $state(app.today);
  let selOcc = $state<Occurrence[]>([]);

  const firstDow = $derived(app.lang === "fr" ? 1 : 0); // Monday-first in French locales

  function monthLen(ym: string) {
    const [y, m] = ym.split("-").map(Number);
    return new Date(Date.UTC(y, m, 0)).getUTCDate();
  }
  function shift(n: number) {
    const [y, m] = month.split("-").map(Number);
    const d = new Date(Date.UTC(y, m - 1 + n, 1));
    month = d.toISOString().slice(0, 7);
  }

  $effect(() => {
    const ym = month;
    void app.config; // refetch when settings change
    api.days(`${ym}-01`, monthLen(ym)).then((d) => (days = d));
  });
  $effect(() => {
    const s = selected;
    void app.items;
    api.occurrences(s, 1).then((o) => (selOcc = o));
  });

  const lead = $derived.by(() => {
    if (!days.length) return 0;
    const dow = new Date(days[0].date + "T00:00:00Z").getUTCDay();
    return (dow - firstDow + 7) % 7;
  });
  const hijriSpan = $derived.by(() => {
    if (!days.length) return "";
    const a = days[0].hijri, b = days[days.length - 1].hijri;
    const n = (h: typeof a) => app.t.hijriMonths[h.month - 1];
    return a.month === b.month ? `${n(a)} ${app.fmt.num(a.year)}` : `${n(a)} – ${n(b)} ${app.fmt.num(b.year)}`;
  });
  const sel = $derived(days.find((d) => d.date === selected));
  const keys: TimeKey[] = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"];
  const wd = $derived([...app.t.weekdaysShort.slice(firstDow), ...app.t.weekdaysShort.slice(0, firstDow)]);
</script>

<div class="layout">
  <section class="glass card">
    <header class="head">
      <button class="nav-btn" onclick={() => shift(-1)} aria-label="previous"><Icon name="chevron" size={18} /></button>
      <div class="title">
        <div class="g">{app.fmt.monthYear(`${month}-01`)}</div>
        <div class="muted">{hijriSpan} {app.t.hijriShort}</div>
      </div>
      <button class="nav-btn" onclick={() => shift(1)} aria-label="next"><Icon name="chevron" size={18} /></button>
    </header>
    <div class="grid" role="grid">
      {#each wd as w}<div class="wd faint">{w}</div>{/each}
      {#each Array(lead) as _}<div></div>{/each}
      {#each days as d}
        <button
          class="cell"
          class:today={d.date === app.today}
          class:sel={d.date === selected}
          class:fri={d.weekday === "friday"}
          class:hstart={d.hijri.day === 1}
          onclick={() => (selected = d.date)}
        >
          <span class="gd">{app.fmt.num(Number(d.date.slice(8)))}</span>
          <span class="hd">{d.hijri.day === 1 ? app.t.hijriMonths[d.hijri.month - 1] : app.fmt.num(d.hijri.day)}</span>
          {#if d.occasions.length}<span class="occ-dot" title={d.occasions.map((o) => app.t.occ[o]).join(", ")}></span>{/if}
        </button>
      {/each}
    </div>
  </section>

  {#if sel}
    <aside class="glass card side">
      <h2>{app.fmt.longDate(sel.date)}</h2>
      <div class="hijri">{app.fmt.num(sel.hijri.day)} {app.t.hijriMonths[sel.hijri.month - 1]} {app.fmt.num(sel.hijri.year)}</div>
      {#each sel.occasions as o}<div class="chip occ"><Icon name="sparkle" size={14} /> {app.t.occ[o]}</div>{/each}
      <dl class="times">
        {#each keys as k}
          <dt class="muted">{app.keyName(k, sel)}</dt><dd class="tabular">{app.fmt.time(sel.times[k])}</dd>
        {/each}
      </dl>
      {#if selOcc.length}
        <ul class="occ-list">
          {#each selOcc as o}
            <li><Icon name={o.kind} size={14} /> <span>{o.title}</span> <span class="tabular muted">{app.fmt.time(o.start)}</span></li>
          {/each}
        </ul>
      {/if}
    </aside>
  {/if}
</div>

<style>
  .layout { display: grid; grid-template-columns: 1fr 300px; gap: 14px; align-items: start; }
  .head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
  .title { text-align: center; }
  .g { font-size: 22px; font-weight: 600; }
  .nav-btn { background: var(--glass); border: 1px solid var(--glass-border); border-radius: 999px; width: 38px; height: 38px; display: grid; place-items: center; }
  .nav-btn:first-child :global(svg) { transform: scaleX(-1); }
  :global([dir="rtl"]) .nav-btn:first-child :global(svg) { transform: none; }
  :global([dir="rtl"]) .nav-btn:last-child :global(svg) { transform: scaleX(-1); }
  .grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6px; }
  .wd { text-align: center; font-size: 12px; padding-bottom: 4px; }
  .cell {
    position: relative; aspect-ratio: 1.1; border-radius: 14px; border: 1px solid transparent;
    background: var(--glass); display: grid; align-content: center; justify-items: center; gap: 2px; padding: 4px;
  }
  .cell:hover { background: var(--glass-strong); }
  .cell.fri { box-shadow: inset 0 -2px 0 var(--glass-border); }
  .cell.today { border-color: var(--accent); }
  .cell.sel { background: var(--glass-strong); border-color: var(--ink-2); }
  .gd { font-size: 17px; font-weight: 600; }
  .hd { font-size: 11px; color: var(--ink-2); max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .cell.hstart .hd { color: var(--accent); font-weight: 600; }
  .occ-dot { position: absolute; top: 7px; inset-inline-end: 7px; width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }
  .side { display: grid; gap: 10px; }
  .side h2 { margin-bottom: 0; }
  .hijri { font-size: 18px; font-weight: 600; }
  .occ { justify-self: start; }
  .times { display: grid; grid-template-columns: 1fr auto; gap: 8px 12px; margin: 6px 0 0; }
  .times dd { margin: 0; font-weight: 500; }
  .occ-list { list-style: none; padding: 10px 0 0; margin: 0; border-top: 1px solid var(--glass-border); display: grid; gap: 8px; }
  .occ-list li { display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: center; }
  @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
</style>

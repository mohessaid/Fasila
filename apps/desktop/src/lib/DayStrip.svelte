<script lang="ts">
  import { app } from "./state.svelte";
  import type { Day, Occurrence, TimeKey } from "./types";
  import { PRAYERS } from "./types";

  let { day, items = [], draft = null }: { day: Day; items?: Occurrence[]; draft?: Occurrence | null } = $props();

  // Span: a little before Fajr until midnight (end of Isha's preferred time).
  const t0 = $derived((day.times.fajr ?? day.times.sunrise ?? 0) - 20 * 60);
  const t1 = $derived(day.times.midnight ?? (day.times.isha ?? t0) + 3 * 3600);
  const pos = (t: number) => Math.min(100, Math.max(0, ((t - t0) / (t1 - t0)) * 100));
  const width = (a: number, b: number) => Math.max(0.6, pos(b) - pos(a));

  const guard = $derived(app.config?.guard ?? { before_minutes: 5, after_minutes: 20, jumuah_extra_minutes: 40 });
  const windows = $derived(
    PRAYERS.filter((k) => day.times[k] !== null).map((k: TimeKey) => {
      const at = day.times[k]!;
      const extra = k === "dhuhr" && day.weekday === "friday" ? guard.jumuah_extra_minutes : 0;
      return { k, at, from: at - guard.before_minutes * 60, to: at + (guard.after_minutes + extra) * 60 };
    }),
  );
  // Free time between protected windows: the fasila itself.
  const gaps = $derived(
    windows.slice(1).map((w, i) => ({ from: windows[i].to, to: w.from })).filter((g) => g.to - g.from > 45 * 60),
  );
  const showNow = $derived(app.now > t0 && app.now < t1);
</script>

<figure class="strip" aria-label={app.t.freeSlots}>
  <div class="track"></div>
  {#each windows as w}
    <div class="win" style:inset-inline-start="{pos(w.from)}%" style:width="{width(w.from, w.to)}%"
      title={`${app.keyName(w.k, day)} ${app.fmt.time(w.at)}`}></div>
    <div class="pname" style:inset-inline-start="{pos(w.at)}%">
      <span>{app.keyName(w.k, day)}</span>
      <span class="ptime tabular">{app.fmt.time(w.at)}</span>
    </div>
  {/each}
  {#each gaps as g}
    <div class="gap tabular" style:inset-inline-start="{(pos(g.from) + pos(g.to)) / 2}%">
      {app.fmt.duration(Math.round((g.to - g.from) / 60), app.t.hours, app.t.minutes)}
    </div>
  {/each}
  {#each items as o}
    {@const end = o.end ?? o.start + 5 * 60}
    <div class="item" class:clash={o.conflicts.length} style:inset-inline-start="{pos(o.start)}%"
      style:width="{width(o.start, end)}%" title={o.title}></div>
  {/each}
  {#if draft}
    {@const end = draft.end ?? draft.start + 5 * 60}
    <div class="draft" class:clash={draft.conflicts.length} style:inset-inline-start="{pos(draft.start)}%"
      style:width="{width(draft.start, end)}%"></div>
  {/if}
  {#if showNow}<div class="now" style:inset-inline-start="{pos(app.now)}%"></div>{/if}
</figure>

<style>
  .strip { position: relative; height: 104px; margin: 8px 0 0; }
  .track { position: absolute; inset-inline: 0; top: 58px; height: 2px; border-radius: 2px; background: var(--hairline); }
  .win { position: absolute; top: 54px; height: 10px; border-radius: 5px; background: rgba(248, 250, 252, 0.22); }
  .pname {
    position: absolute; top: 6px; translate: -50% 0; display: grid; justify-items: center; gap: 1px;
    font-size: 12px; font-weight: 600; color: var(--ink-2); white-space: nowrap;
  }
  :global([dir="rtl"]) .pname, :global([dir="rtl"]) .gap { translate: 50% 0; }
  .ptime { font-weight: 400; color: var(--ink-3); font-size: 11px; }
  .gap { position: absolute; top: 40px; translate: -50% 0; font-size: 10.5px; color: var(--ink-3); white-space: nowrap; }
  .item { position: absolute; top: 74px; height: 6px; border-radius: 3px; background: color-mix(in srgb, var(--accent) 75%, transparent); }
  .draft { position: absolute; top: 86px; height: 8px; border-radius: 4px; border: 1.5px dashed var(--accent); }
  .item.clash { background: var(--warn); }
  .draft.clash { border-color: var(--warn); }
  .now { position: absolute; top: 52px; bottom: 4px; width: 1.5px; background: var(--ink); opacity: 0.7; }
  .now::before { content: ""; position: absolute; top: -3px; left: -2.25px; width: 6px; height: 6px; border-radius: 50%; background: var(--ink); }
</style>

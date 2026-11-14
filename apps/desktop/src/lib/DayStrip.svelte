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
  /* The day as a frosted glass channel. Prayer windows are frosted segments in it,
     items and the draft are lit capsules, and "now" is a soft beam of light. */
  .strip { position: relative; height: 112px; margin: 8px 0 0; }
  .track {
    position: absolute; inset-inline: 0; top: 50px; height: 40px; border-radius: 14px;
    background: rgba(255, 255, 255, 0.05);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), inset 0 -10px 20px rgba(6, 10, 20, 0.18);
  }
  .win {
    position: absolute; top: 54px; height: 32px; border-radius: 10px;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.1));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3);
  }
  .pname {
    position: absolute; top: 4px; translate: -50% 0; display: grid; justify-items: center; gap: 1px;
    font-size: 12px; font-weight: 600; color: var(--ink-2); white-space: nowrap;
  }
  :global([dir="rtl"]) .pname, :global([dir="rtl"]) .gap { translate: 50% 0; }
  .ptime { font-weight: 400; color: var(--ink-2); font-size: 11px; }
  .gap { position: absolute; top: 96px; translate: -50% 0; font-size: 11px; color: var(--ink-2); white-space: nowrap; }
  .item {
    position: absolute; top: 62px; height: 16px; min-width: 8px; border-radius: 8px;
    background: linear-gradient(180deg, #ffbe8f, var(--accent));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5), 0 4px 12px rgba(255, 155, 84, 0.35);
  }
  .item.clash { background: linear-gradient(180deg, #ffd29b, var(--warn)); }
  .draft {
    position: absolute; top: 58px; height: 24px; min-width: 10px; border-radius: 10px;
    background: radial-gradient(120% 160% at 50% -20%, rgba(255, 155, 84, 0.45), transparent 70%), rgba(255, 255, 255, 0.24);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5), 0 8px 22px rgba(255, 155, 84, 0.3);
    animation: breathe 2.4s ease-in-out infinite alternate;
  }
  .draft.clash { background: radial-gradient(120% 160% at 50% -20%, rgba(255, 178, 107, 0.6), transparent 70%), rgba(255, 255, 255, 0.24); }
  @keyframes breathe { from { opacity: 0.75; } to { opacity: 1; } }
  :global(:root[data-motion="reduce"]) .draft { animation: none; }
  .now {
    position: absolute; top: 46px; height: 48px; width: 2px; translate: -1px 0; border-radius: 2px;
    background: linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.95), transparent);
    box-shadow: 0 0 10px rgba(255, 255, 255, 0.55);
  }
</style>

<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import Composer from "./Composer.svelte";
  import DayStrip from "./DayStrip.svelte";
  import Icon from "./Icon.svelte";
  import type { Item, Occurrence } from "./types";

  let editing = $state<Item | null>(null);
  let draft = $state<Occurrence | null>(null);
  let descs = $state<Record<string, string>>({});

  $effect(() => {
    const items = app.items;
    const lang = app.lang;
    Promise.all(
      items.map(async (i) => {
        let d = await api.describe(i.start, lang);
        if (i.end.type === "until") d += ` → ${await api.describe(i.end.anchor, lang)}`;
        else if (i.end.type === "duration") d += ` · ${app.fmt.duration(i.end.minutes, app.t.hours, app.t.minutes)}`;
        return [i.id, d] as const;
      }),
    ).then((pairs) => (descs = Object.fromEntries(pairs)));
  });

  function recLabel(i: Item) {
    const r = i.recurrence;
    switch (r.type) {
      case "daily": return app.t.daily;
      case "once": return `${app.t.once} · ${app.fmt.dayMonth(r.date)}`;
      case "weekly": return app.t.weekdaysShort.filter((_, d) => (r.days >> d) & 1).join(" · ");
      case "hijri_month": return app.t.ramadan;
      case "hijri_monthly": return app.t.whiteDays;
      case "hijri_yearly": return `${app.fmt.num(r.day)} ${app.t.hijriMonths[r.month - 1]}`;
    }
  }
  const occById = $derived(Object.fromEntries(app.todayOcc.map((o) => [o.item_id, o])));
  // Items in the order they happen today; items not happening today go last.
  const ordered = $derived(
    [...app.items].sort((a, b) => (occById[a.id]?.start ?? Infinity) - (occById[b.id]?.start ?? Infinity)),
  );
</script>

<div class="page">
  <section class="glass panel">
    <Composer {editing} bind:preview={draft} onDone={() => (editing = null)} />
    {#if app.todayDay}
      <div class="label strip-label">{app.t.freeSlots}</div>
      <DayStrip day={app.todayDay} items={app.todayOcc} {draft} />
    {/if}
  </section>

  <section class="glass panel">
    <div class="label">{app.t.items}</div>
    {#if app.items.length === 0}
      <p class="empty">{app.t.emptyItems}</p>
    {:else}
      <ul class="items">
        {#each ordered as item (item.id)}
          {@const occ = occById[item.id]}
          <li class="frost" class:off={!item.enabled} class:lit={editing?.id === item.id}>
            <span class="when tabular">{occ ? app.fmt.time(occ.start) : "—"}</span>
            <button class="body" onclick={() => (editing = item)} title={app.t.edit}>
              <span class="title"><Icon name={item.kind} size={14} /> {item.title}</span>
              <span class="meta">{app.fmt.digits(descs[item.id] ?? "")} · {recLabel(item)}</span>
              {#if occ?.conflicts.length}
                <span class="clash">{app.t.conflict} {occ.conflicts.map((c) => app.t.keys[c.prayer]).join(", ")}</span>
              {/if}
            </button>
            <label class="switch" title={item.enabled ? "On" : "Off"}>
              <input type="checkbox" checked={item.enabled} onchange={(e) => app.upsertItem({ ...item, enabled: e.currentTarget.checked })} />
              <span></span>
            </label>
            <button class="del" onclick={() => app.removeItem(item.id)} aria-label={app.t.delete}><Icon name="trash" size={15} /></button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .page { display: grid; gap: 18px; max-width: 900px; margin: 0 auto; padding: 8px 4px 20px; }
  .panel { padding: 26px 30px; }
  .strip-label { margin-top: 26px; margin-bottom: 0; }
  .empty { margin: 0; color: var(--ink-2); }
  .items { list-style: none; margin: 0 -16px; padding: 0; display: grid; gap: 2px; }
  .items li { display: grid; grid-template-columns: 56px 1fr auto auto; gap: 16px; align-items: center; padding: 13px 16px; }
  .items li.off { opacity: 0.45; }
  .when { color: var(--ink-2); font-size: 15px; }
  .body { display: grid; gap: 3px; text-align: start; background: none; border: 0; padding: 0; }
  .title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; }
  .title :global(svg) { color: var(--ink-3); }
  .meta { font-size: 13px; color: var(--ink-3); }
  .clash { font-size: 12.5px; color: var(--warn); }
  .del { background: none; border: 0; color: var(--ink-3); padding: 6px; border-radius: 8px; opacity: 0; transition: opacity 0.15s, color 0.15s; }
  .items li:hover .del, .del:focus-visible { opacity: 1; }
  .del:hover { color: var(--danger); }
  .switch { position: relative; width: 34px; height: 20px; }
  .switch input { opacity: 0; position: absolute; inset: 0; margin: 0; cursor: pointer; }
  .switch span { position: absolute; inset: 0; border-radius: 999px; background: rgba(255, 255, 255, 0.1); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1); pointer-events: none; transition: background 0.2s, box-shadow 0.2s; }
  .switch span::after { content: ""; position: absolute; top: 3px; inset-inline-start: 3px; width: 14px; height: 14px; border-radius: 50%; background: var(--ink-2); transition: inset-inline-start 0.2s, background 0.2s; }
  .switch input:checked + span { background: linear-gradient(180deg, #ffb27a, var(--accent)); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45), 0 4px 12px rgba(255, 155, 84, 0.3); }
  .switch input:checked + span::after { inset-inline-start: 17px; background: var(--accent-ink); }
</style>

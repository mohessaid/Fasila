<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import Composer from "./Composer.svelte";
  import Icon from "./Icon.svelte";
  import type { Item, Slot } from "./types";

  let editing = $state<Item | null>(null);
  let descs = $state<Record<string, string>>({});
  let slots = $state<Slot[]>([]);
  let slotDescs = $state<string[]>([]);

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

  $effect(() => {
    const today = app.today;
    const lang = app.lang;
    api.freeSlots(today, 60).then(async (s) => {
      slots = s;
      slotDescs = await Promise.all(s.map((x) => api.describe(x.anchor, lang)));
    });
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
</script>

<div class="layout">
  <div class="main">
    <Composer {editing} onDone={() => (editing = null)} />

    <section class="glass card">
      <h2>{app.t.items}</h2>
      {#if app.items.length === 0}
        <p class="muted">{app.t.emptyItems}</p>
      {:else}
        <ul class="items">
          {#each app.items as item (item.id)}
            {@const occ = occById[item.id]}
            <li class:off={!item.enabled} class:editing={editing?.id === item.id}>
              <span class="kind"><Icon name={item.kind} size={16} /></span>
              <button class="body" onclick={() => (editing = item)} title={app.t.edit}>
                <span class="title">{item.title}</span>
                <span class="muted small">{app.fmt.digits(descs[item.id] ?? "")}</span>
                <span class="faint small">{recLabel(item)}</span>
              </button>
              <span class="when tabular">
                {#if occ}{app.fmt.time(occ.start)}{/if}
                {#if occ?.conflicts.length}<span class="badge"><Icon name="warn" size={11} /> {app.t.conflict}</span>{/if}
              </span>
              <label class="switch" title={item.enabled ? "On" : "Off"}>
                <input type="checkbox" checked={item.enabled} onchange={(e) => app.upsertItem({ ...item, enabled: e.currentTarget.checked })} />
                <span></span>
              </label>
              <button class="icon-btn" onclick={() => app.removeItem(item.id)} aria-label={app.t.delete}><Icon name="trash" size={16} /></button>
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  </div>

  <aside class="glass card">
    <h2>{app.t.freeSlots}</h2>
    <ul class="slots">
      {#each slots as s, i}
        <li>
          <span class="tabular">{app.fmt.time(s.start)}</span>
          <span class="muted small">{app.fmt.digits(slotDescs[i] ?? "")}</span>
        </li>
      {/each}
    </ul>
    <p class="faint small">{app.t.protect}: {app.fmt.num(app.config?.guard.before_minutes ?? 5)} / {app.fmt.num(app.config?.guard.after_minutes ?? 20)} {app.t.minutes}</p>
  </aside>
</div>

<style>
  .layout { display: grid; grid-template-columns: 1fr 280px; gap: 14px; align-items: start; }
  .main { display: grid; gap: 14px; }
  .items { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
  .items li { display: grid; grid-template-columns: auto 1fr auto auto auto; gap: 12px; align-items: center; padding: 10px 12px; border-radius: 16px; background: var(--glass); border: 1px solid transparent; }
  .items li.editing { border-color: var(--accent); }
  .items li.off { opacity: 0.5; }
  .body { display: grid; gap: 2px; text-align: start; background: none; border: 0; padding: 0; }
  .title { font-weight: 600; }
  .small { font-size: 12.5px; }
  .kind { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 11px; background: var(--glass-strong); }
  .when { display: grid; justify-items: end; gap: 4px; font-size: 15px; }
  .icon-btn { background: none; border: 0; color: var(--ink-2); padding: 6px; border-radius: 10px; }
  .icon-btn:hover { background: var(--glass-strong); color: var(--ink); }
  .switch { position: relative; width: 38px; height: 22px; }
  .switch input { opacity: 0; position: absolute; inset: 0; margin: 0; cursor: pointer; }
  .switch span { position: absolute; inset: 0; border-radius: 999px; background: var(--glass-strong); border: 1px solid var(--glass-border); pointer-events: none; transition: background 0.2s; }
  .switch span::after { content: ""; position: absolute; top: 2px; inset-inline-start: 2px; width: 16px; height: 16px; border-radius: 50%; background: var(--ink); transition: inset-inline-start 0.2s; }
  .switch input:checked + span { background: var(--accent); }
  .switch input:checked + span::after { inset-inline-start: 18px; background: var(--accent-ink); }
  .slots { list-style: none; margin: 0 0 12px; padding: 0; display: grid; gap: 8px; }
  .slots li { display: grid; gap: 2px; padding: 10px 12px; border-radius: 14px; background: var(--glass); }
  @media (max-width: 980px) { .layout { grid-template-columns: 1fr; } }
</style>

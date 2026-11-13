<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import { addDays } from "./format";
  import Icon from "./Icon.svelte";
  import { ANCHOR_KEYS, type Item, type Occurrence, type Slot, type TimeKey } from "./types";

  let { editing = null, onDone }: { editing?: Item | null; onDone?: () => void } = $props();

  const blank = (): Item => ({
    id: "", title: "", kind: "alarm", start: { key: "fajr", offset_minutes: -15 }, end: { type: "none" },
    recurrence: { type: "daily" }, enabled: true, remind_before_minutes: null, notes: "",
  });

  let text = $state("");
  let draft = $state<Item>(blank());
  let parseError = $state<string | null>(null);
  let preview = $state<Occurrence | null>(null);
  let suggestion = $state<Slot | null>(null);
  let description = $state("");
  let timer: ReturnType<typeof setTimeout> | undefined;

  $effect(() => {
    if (editing) {
      draft = structuredClone($state.snapshot(editing)) as Item;
      text = "";
    }
  });

  // --- text → chips (shared Rust parser) -----------------------------------
  function onText() {
    clearTimeout(timer);
    timer = setTimeout(async () => {
      if (!text.trim()) { parseError = null; return; }
      try {
        const r = await api.parse(text, app.lang, app.today);
        parseError = null;
        const p = r.parsed;
        draft = {
          ...draft,
          title: p.title || draft.title,
          start: p.start,
          end: p.end,
          kind: p.kind ?? (p.end.type === "none" ? "alarm" : "event"),
          recurrence: p.recurrence ?? draft.recurrence,
        };
      } catch (e) {
        parseError = String(e) === "no_prayer" ? app.t.noPrayer : null;
      }
    }, 160);
  }

  // --- chips helpers ---------------------------------------------------------
  let direction = $derived(draft.start.offset_minutes < 0 ? "before" : draft.start.offset_minutes > 0 ? "after" : "at");
  let amount = $derived(Math.abs(draft.start.offset_minutes));

  function setDirection(d: string) {
    const a = amount || 15;
    draft.start.offset_minutes = d === "before" ? -a : d === "after" ? a : 0;
  }
  function setAmount(v: number) {
    const a = Math.max(0, Math.min(720, Math.round(v || 0)));
    draft.start.offset_minutes = direction === "before" ? -a : direction === "after" ? a : 0;
    if (a > 0 && direction === "at") draft.start.offset_minutes = a;
  }

  let endType = $derived(draft.end.type);
  function setEnd(t: string) {
    if (t === "none") draft.end = { type: "none" };
    else if (t === "duration") draft.end = { type: "duration", minutes: 60 };
    else {
      const nextKey = ANCHOR_KEYS[Math.min(ANCHOR_KEYS.indexOf(draft.start.key) + 1, ANCHOR_KEYS.length - 1)];
      draft.end = { type: "until", anchor: { key: nextKey, offset_minutes: -10 } };
    }
  }

  const recValue = $derived.by(() => {
    const r = draft.recurrence;
    if (r.type === "once") return r.date === app.today ? "today" : r.date === addDays(app.today, 1) ? "tomorrow" : "once";
    if (r.type === "hijri_month") return "ramadan";
    if (r.type === "hijri_monthly") return "white";
    return r.type;
  });
  function setRec(v: string) {
    if (v === "daily") draft.recurrence = { type: "daily" };
    else if (v === "today") draft.recurrence = { type: "once", date: app.today };
    else if (v === "tomorrow") draft.recurrence = { type: "once", date: addDays(app.today, 1) };
    else if (v === "weekly") draft.recurrence = { type: "weekly", days: 1 << 5 };
    else if (v === "ramadan") draft.recurrence = { type: "hijri_month", month: 9 };
    else if (v === "white") draft.recurrence = { type: "hijri_monthly", days: [13, 14, 15] };
  }
  function toggleDay(i: number) {
    if (draft.recurrence.type !== "weekly") return;
    const days = draft.recurrence.days ^ (1 << i);
    draft.recurrence = { type: "weekly", days: days || 1 << i };
  }

  // --- live preview: resolved time today, conflicts, better slot -------------
  $effect(() => {
    const item = $state.snapshot(draft) as Item;
    let cancelled = false;
    (async () => {
      description = await api.describe(item.start, app.lang);
      const occ = await api.preview(item, app.today);
      if (cancelled) return;
      preview = occ[0] ?? null;
      suggestion = null;
      if (preview && preview.conflicts.length && item.kind !== "alarm") {
        const mins = preview.end ? Math.round((preview.end - preview.start) / 60) : 30;
        const slots = await api.freeSlots(app.today, mins);
        if (!cancelled) {
          // The free slot closest to what was asked for.
          suggestion = slots.sort((a, b) => Math.abs(a.start - preview!.start) - Math.abs(b.start - preview!.start))[0] ?? null;
        }
      }
    })();
    return () => { cancelled = true; };
  });

  async function applySuggestion() {
    if (!suggestion) return;
    const len = draft.end.type === "until" && preview?.end ? Math.round((preview.end - preview.start) / 60) : null;
    draft.start = { ...suggestion.anchor };
    if (len) draft.end = { type: "duration", minutes: len };
  }

  async function save() {
    if (!draft.title.trim()) draft.title = app.t[draft.kind];
    await app.upsertItem($state.snapshot(draft) as Item);
    app.flash(app.t.saved);
    text = "";
    draft = blank();
    onDone?.();
  }
  function cancel() {
    text = "";
    draft = blank();
    onDone?.();
  }
</script>

<section class="glass card composer">
  <label class="nl">
    <Icon name="sparkle" />
    <input
      class="input"
      bind:value={text}
      oninput={onText}
      onkeydown={(e) => e.key === "Enter" && !parseError && save()}
      placeholder={app.t.composerPlaceholder}
      aria-describedby="composer-hint"
      dir="auto"
    />
  </label>
  <p id="composer-hint" class="hint" class:err={parseError}>{parseError ?? app.t.composerHint}</p>

  <div class="chips">
    <label class="chip title"><span class="faint">{app.t.titleLabel}</span><input bind:value={draft.title} dir="auto" placeholder={app.t[draft.kind]} /></label>
    <label class="chip">
      <Icon name={draft.kind} size={14} />
      <select bind:value={draft.kind} aria-label={app.t.kind}>
        <option value="alarm">{app.t.alarm}</option><option value="task">{app.t.task}</option><option value="event">{app.t.event}</option>
      </select>
    </label>
  </div>

  <div class="chips sentence">
    {#if direction !== "at"}
      <label class="chip"><input type="number" min="0" max="720" value={amount} oninput={(e) => setAmount(+e.currentTarget.value)} aria-label={app.t.minutes} /> {app.t.minutes}</label>
    {/if}
    <label class="chip">
      <select value={direction} onchange={(e) => setDirection(e.currentTarget.value)}>
        <option value="before">{app.t.before}</option><option value="at">{app.t.atPrayer}</option><option value="after">{app.t.after}</option>
      </select>
    </label>
    <label class="chip strong">
      <select bind:value={draft.start.key}>
        {#each ANCHOR_KEYS as k}<option value={k}>{app.t.keys[k]}</option>{/each}
      </select>
    </label>
    <span class="dot">·</span>
    <label class="chip">
      <select value={endType} onchange={(e) => setEnd(e.currentTarget.value)} aria-label={app.t.end}>
        <option value="none">{app.t.endNone}</option><option value="duration">{app.t.endDuration}</option><option value="until">{app.t.endUntil}</option>
      </select>
    </label>
    {#if draft.end.type === "duration"}
      <label class="chip"><input type="number" min="5" max="720" step="5" bind:value={draft.end.minutes} /> {app.t.minutes}</label>
    {:else if draft.end.type === "until"}
      {@const u = draft.end.anchor}
      <label class="chip"><input type="number" min="0" max="720" value={Math.abs(u.offset_minutes)}
        oninput={(e) => (u.offset_minutes = (u.offset_minutes <= 0 ? -1 : 1) * Math.abs(+e.currentTarget.value))} /> {app.t.minutes}</label>
      <label class="chip"><select value={u.offset_minutes <= 0 ? "before" : "after"} onchange={(e) => (u.offset_minutes = (e.currentTarget.value === "before" ? -1 : 1) * Math.abs(u.offset_minutes))}>
        <option value="before">{app.t.before}</option><option value="after">{app.t.after}</option></select></label>
      <label class="chip strong"><select bind:value={u.key}>{#each ANCHOR_KEYS as k}<option value={k}>{app.t.keys[k] as string}</option>{/each}</select></label>
    {/if}
    <span class="dot">·</span>
    <label class="chip">
      <select value={recValue} onchange={(e) => setRec(e.currentTarget.value)} aria-label={app.t.repeat}>
        <option value="daily">{app.t.daily}</option>
        <option value="today">{app.t.once} · {app.t.today}</option>
        <option value="tomorrow">{app.t.once} · +1</option>
        <option value="weekly">{app.t.weekly}</option>
        <option value="ramadan">{app.t.ramadan}</option>
        <option value="white">{app.t.whiteDays}</option>
      </select>
    </label>
    <label class="chip">
      <Icon name="bell" size={14} />
      <select value={String(draft.remind_before_minutes ?? 0)} onchange={(e) => (draft.remind_before_minutes = +e.currentTarget.value || null)} aria-label={app.t.remind}>
        <option value="0">{app.t.none}</option>
        {#each [5, 10, 15, 30, 60] as m}<option value={String(m)}>{app.t.remind} {app.fmt.num(m)} {app.t.minutes}</option>{/each}
      </select>
    </label>
  </div>

  {#if draft.recurrence.type === "weekly"}
    {@const mask = draft.recurrence.days}
    <div class="days" role="group" aria-label={app.t.weekly}>
      {#each app.t.weekdaysShort as d, i}
        <button class="chip" class:on={(mask >> i) & 1} aria-pressed={!!((mask >> i) & 1)} onclick={() => toggleDay(i)}>{d}</button>
      {/each}
    </div>
  {/if}

  <div class="preview">
    <div>
      <div class="desc">{app.fmt.digits(description)}</div>
      {#if preview}
        <div class="muted tabular">{app.t.previewToday} {app.fmt.time(preview.start)}{#if preview.end} – {app.fmt.time(preview.end)}{/if}</div>
      {/if}
    </div>
    <div class="actions">
      {#if editing}<button class="btn ghost" onclick={cancel}>{app.t.cancel}</button>{/if}
      <button class="btn primary" onclick={save}><Icon name={editing ? "task" : "plus"} size={16} /> {editing ? app.t.save : app.t.add}</button>
    </div>
  </div>

  {#if preview?.conflicts.length}
    <div class="warn conflict" role="alert">
      <Icon name="warn" size={16} />
      <span>{app.t.conflictLong} {preview.conflicts.map((c) => app.t.keys[c.prayer]).join(" · ")}.</span>
      {#if suggestion}
        {#await api.describe(suggestion.anchor, app.lang) then s}
          <button class="btn" onclick={applySuggestion}>{app.t.suggest}: {app.fmt.digits(s)} ({app.fmt.time(suggestion.start)})</button>
        {/await}
      {/if}
    </div>
  {/if}
</section>

<style>
  .composer { display: grid; gap: 12px; }
  .nl { display: flex; align-items: center; gap: 10px; }
  .nl .input { font-size: 17px; padding: 14px 18px; border-radius: 18px; }
  .hint { margin: -4px 4px 0; font-size: 12.5px; color: var(--ink-3); }
  .hint.err { color: var(--warn); }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  .chip.title { flex: 1 1 220px; }
  .chip.title input { flex: 1; min-width: 0; }
  .chip.strong { background: var(--glass-strong); font-weight: 600; }
  .dot { color: var(--ink-3); }
  .days { display: flex; gap: 6px; flex-wrap: wrap; }
  .days .chip.on { background: var(--accent); color: #141a33; border-color: transparent; }
  .preview { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; padding-top: 6px; border-top: 1px solid var(--glass-border); }
  .desc { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; }
  .actions .btn { display: inline-flex; gap: 6px; align-items: center; }
  .conflict { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>

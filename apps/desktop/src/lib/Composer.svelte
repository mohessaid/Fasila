<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import { addDays } from "./format";
  import Icon from "./Icon.svelte";
  import Token from "./Token.svelte";
  import { ANCHOR_KEYS, type Item, type Occurrence, type Slot, type TimeKey } from "./types";

  let {
    editing = null,
    onDone,
    preview = $bindable(null),
  }: { editing?: Item | null; onDone?: () => void; preview?: Occurrence | null } = $props();

  const blank = (): Item => ({
    id: "", title: "", kind: "alarm", start: { key: "fajr", offset_minutes: -15 }, end: { type: "none" },
    recurrence: { type: "daily" }, enabled: true, remind_before_minutes: null, notes: "",
  });

  let text = $state("");
  let draft = $state<Item>(blank());
  let parseError = $state<string | null>(null);
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

  const keyOptions = $derived(ANCHOR_KEYS.map((k) => ({ value: k, label: app.t.keys[k] })));

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

<section class="composer">
  <label class="line">
    <Icon name="sparkle" />
    <input
      bind:value={text}
      oninput={onText}
      onkeydown={(e) => e.key === "Enter" && !parseError && save()}
      placeholder={app.t.composerPlaceholder}
      aria-describedby="composer-hint"
      dir="auto"
    />
  </label>
  <p id="composer-hint" class="hint" class:err={parseError}>{parseError ?? app.t.composerHint}</p>

  <!-- The item as one sentence of editable words. -->
  <p class="sentence">
    <Token label={app.t.kind} value={draft.kind} onchange={(v) => (draft.kind = v as Item["kind"])}
      options={[{ value: "alarm", label: app.t.alarm }, { value: "task", label: app.t.task }, { value: "event", label: app.t.event }]} />
    <input class="token title" bind:value={draft.title} placeholder={app.t.titleLabel} dir="auto"
      style:width="{Math.max(6, (draft.title || app.t.titleLabel).length + 1)}ch" aria-label={app.t.titleLabel} />
    {#snippet amountTok()}
      {#if direction !== "at"}
        <span class="nowrap"><input class="token num" type="number" min="0" max="720" value={amount}
          oninput={(e) => setAmount(+e.currentTarget.value)} aria-label={app.t.minutes} /> {app.t.minutes}</span>
      {/if}
    {/snippet}
    {#snippet dirTok()}
      <Token value={direction} onchange={setDirection}
        options={[{ value: "before", label: app.t.before }, { value: "at", label: app.t.atPrayer }, { value: "after", label: app.t.after }]} />
    {/snippet}
    {#snippet keyTok()}
      <Token tone="strong" value={draft.start.key} onchange={(v) => (draft.start.key = v as TimeKey)} options={keyOptions} />
    {/snippet}
    {#if app.lang === "ar"}{@render dirTok()} {@render keyTok()} {@render amountTok()}
    {:else}{@render amountTok()} {@render dirTok()} {@render keyTok()}{/if}
    <Token label={app.t.end} value={endType} onchange={setEnd}
      options={[{ value: "none", label: app.t.endNone }, { value: "duration", label: app.t.endDuration }, { value: "until", label: app.t.endUntil }]} />
    {#if draft.end.type === "duration"}
      <span class="nowrap"><input class="token num" type="number" min="5" max="720" step="5" bind:value={draft.end.minutes} /> {app.t.minutes}</span>
    {:else if draft.end.type === "until"}
      {@const u = draft.end.anchor}
      {@const uDir = u.offset_minutes < 0 ? "before" : u.offset_minutes > 0 ? "after" : "at"}
      {#snippet untilDir()}
        <Token tone={uDir === "at" ? "quiet" : "normal"} value={uDir}
          onchange={(v) => (u.offset_minutes = v === "at" ? 0 : (v === "before" ? -1 : 1) * (Math.abs(u.offset_minutes) || 10))}
          options={[{ value: "before", label: app.t.before }, { value: "at", label: app.t.onTime }, { value: "after", label: app.t.after }]} />
      {/snippet}
      {#if uDir === "at"}
        <Token tone="strong" value={u.key} onchange={(v) => (u.key = v as TimeKey)} options={keyOptions} />
        {@render untilDir()}
      {:else}
        <span class="nowrap"><input class="token num" type="number" min="1" max="720" value={Math.abs(u.offset_minutes)}
          oninput={(e) => (u.offset_minutes = (u.offset_minutes < 0 ? -1 : 1) * Math.max(1, Math.abs(+e.currentTarget.value)))} /> {app.t.minutes}</span>
        {@render untilDir()}
        <Token tone="strong" value={u.key} onchange={(v) => (u.key = v as TimeKey)} options={keyOptions} />
      {/if}
    {/if}
    <Token label={app.t.repeat} value={recValue} onchange={setRec}
      options={[
        { value: "daily", label: app.t.daily },
        { value: "today", label: app.t.today },
        { value: "tomorrow", label: app.t.tomorrow },
        { value: "weekly", label: app.t.weekly },
        { value: "ramadan", label: app.t.ramadan },
        { value: "white", label: app.t.whiteDays },
      ]} />
    <Token tone="quiet" label={app.t.remind} value={String(draft.remind_before_minutes ?? 0)}
      onchange={(v) => (draft.remind_before_minutes = +v || null)}
      options={[{ value: "0", label: `${app.t.remind}: ${app.t.none}` },
        ...[5, 10, 15, 30, 60].map((m) => ({ value: String(m), label: `${app.t.remind} ${app.fmt.num(m)} ${app.t.minutes}` }))]} />
  </p>

  {#if draft.recurrence.type === "weekly"}
    {@const mask = draft.recurrence.days}
    <div class="days" role="group" aria-label={app.t.weekly}>
      {#each app.t.weekdaysShort as d, i}
        <button class:on={(mask >> i) & 1} aria-pressed={!!((mask >> i) & 1)} onclick={() => toggleDay(i)}>{d}</button>
      {/each}
    </div>
  {/if}

  <footer class="result">
    <div class="read">
      <strong>{app.fmt.digits(description)}</strong>
      {#if preview}
        <span class="muted tabular">{app.t.previewToday} {app.fmt.time(preview.start)}{#if preview.end}{" "}{app.t.rangeTo} {app.fmt.time(preview.end)}{/if}</span>
      {/if}
      {#if preview?.conflicts.length}
        <span class="clash" role="alert">
          <Icon name="warn" size={14} />
          {app.t.conflictLong} {preview.conflicts.map((c) => app.t.keys[c.prayer]).join(", ")}.
          {#if suggestion}
            {#await api.describe(suggestion.anchor, app.lang) then s}
              <button class="link" onclick={applySuggestion}>{app.t.suggest} {app.fmt.digits(s)} ({app.fmt.time(suggestion.start)})</button>
            {/await}
          {/if}
        </span>
      {/if}
    </div>
    <div class="actions">
      {#if editing}<button class="btn ghost" onclick={cancel}>{app.t.cancel}</button>{/if}
      <button class="btn primary" onclick={save}><Icon name={editing ? "task" : "plus"} size={16} /> {editing ? app.t.save : app.t.add}</button>
    </div>
  </footer>
</section>

<style>
  .composer { display: grid; gap: 10px; }
  .line {
    display: flex; align-items: center; gap: 12px; padding: 12px 18px; border-radius: 18px;
    background: rgba(255, 255, 255, 0.06); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
    color: var(--ink-3); transition: background 0.25s, box-shadow 0.25s, color 0.25s;
  }
  .line:hover { background: rgba(255, 255, 255, 0.09); }
  .line:focus-within {
    color: var(--accent);
    background: radial-gradient(120% 200% at 50% -40%, rgba(255, 155, 84, 0.16), transparent 60%), rgba(255, 255, 255, 0.14);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.32), 0 12px 30px rgba(255, 155, 84, 0.14);
  }
  .line input { flex: 1; background: none; border: 0; outline: none; color: var(--ink); font-size: 22px; font-weight: 400; letter-spacing: -0.01em; padding: 4px 0; }
  .line input::placeholder { color: var(--ink-3); }
  .hint { margin: 0 4px; font-size: 12.5px; color: var(--ink-3); }
  .hint.err { color: var(--warn); }
  .sentence { margin: 8px 0 0; font-size: 16px; line-height: 2.4; color: var(--ink-2); word-spacing: 2px; }
  .sentence .title { min-width: 6ch; max-width: 26ch; font-weight: 600; }
  .sentence .title::placeholder { color: var(--ink-3); font-weight: 500; }
  .sentence .num { width: 4.4ch; padding: 3px 6px; text-align: center; -moz-appearance: textfield; appearance: textfield; }
  .sentence .num::-webkit-inner-spin-button { display: none; }

  .nowrap { white-space: nowrap; }
  .days { display: flex; gap: 4px; flex-wrap: wrap; }
  .days button { background: none; border: 0; color: var(--ink-2); padding: 6px 12px; border-radius: 999px; font-size: 13.5px; transition: background 0.2s; }
  .days button:hover { background: rgba(255, 255, 255, 0.08); }
  .days button.on {
    color: var(--ink); font-weight: 600;
    background: radial-gradient(120% 160% at 50% -20%, rgba(255, 155, 84, 0.3), transparent 70%), rgba(255, 255, 255, 0.16);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.32), 0 6px 16px rgba(255, 155, 84, 0.16);
  }
  .result { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; margin-top: 6px; }
  .read { display: grid; gap: 4px; font-size: 14.5px; }
  .read strong { font-size: 16px; font-weight: 600; color: var(--ink); }
  .clash { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; color: var(--warn); font-size: 13.5px; }
  .link {
    border: 0; padding: 4px 12px; border-radius: 999px; color: var(--ink); font-weight: 600; font-size: 13px;
    background: radial-gradient(120% 160% at 50% -20%, rgba(255, 155, 84, 0.32), transparent 70%), rgba(255, 255, 255, 0.14);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3), 0 6px 16px rgba(255, 155, 84, 0.16);
  }
  .link:hover { background: radial-gradient(120% 160% at 50% -20%, rgba(255, 155, 84, 0.45), transparent 70%), rgba(255, 255, 255, 0.2); }
  .actions { display: flex; gap: 8px; }
</style>

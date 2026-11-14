<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import Icon from "./Icon.svelte";
  import type { Adjustments, City, Config, Prefs } from "./types";

  // Work on a copy; persist on change.
  let cfg = $state<Config>(structuredClone($state.snapshot(app.config!)) as Config);
  let prefs = $state<Prefs>(structuredClone($state.snapshot(app.prefs)) as Prefs);
  let q = $state("");
  let feedMsg = $state("");
  let autostart = $state(false);

  api.autostart.isEnabled().then((v) => (autostart = v)).catch(() => {});

  const matches = $derived(
    q.trim().length < 2 ? [] : app.cities.filter((c) => (c.name + " " + c.name_ar).toLowerCase().includes(q.trim().toLowerCase())).slice(0, 6),
  );

  async function persist() {
    await app.saveConfig(structuredClone($state.snapshot(cfg)) as Config);
  }
  async function persistPrefs() {
    await app.savePrefs(structuredClone($state.snapshot(prefs)) as Prefs);
  }

  async function pickCity(c: City) {
    const keepParams = cfg.location.country.toUpperCase() === c.country.toUpperCase();
    const fresh = await api.defaultConfig(app.lang === "ar" ? c.name_ar : c.name, c.latitude, c.longitude, c.timezone, c.country);
    cfg = keepParams ? { ...cfg, location: fresh.location } : { ...fresh, guard: cfg.guard };
    q = "";
    await persist();
  }

  async function setMethod(id: string) {
    const p = await api.methodParams(id);
    cfg.params = { ...p, madhab: cfg.params.madhab, adjustments: cfg.params.adjustments, high_latitude_rule: cfg.params.high_latitude_rule, polar_rule: cfg.params.polar_rule, rounding: cfg.params.rounding };
    await persist();
  }

  const adjKeys: (keyof Adjustments)[] = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"];
  function bump(k: keyof Adjustments, d: number) {
    cfg.params.adjustments[k] = Math.max(-30, Math.min(30, cfg.params.adjustments[k] + d));
    persist();
  }

  async function refreshFeed() {
    try {
      const r = await api.refreshHijri();
      feedMsg = `${r.online ? app.t.feedOnline : app.t.feedOffline} · ${app.fmt.num(r.changed)} ${app.t.changes}${r.source ? " · " + r.source : ""}`;
      cfg = structuredClone($state.snapshot((await api.load()).config!)) as Config;
      await app.refresh();
    } catch (e) {
      feedMsg = String(e);
    }
  }

  async function toggleAutostart() {
    autostart ? await api.autostart.disable() : await api.autostart.enable();
    autostart = await api.autostart.isEnabled();
  }

  const prayerKeys = ["fajr", "dhuhr", "asr", "maghrib", "isha"] as const;
</script>

<div class="cols">
  <section class="glass card">
    <h2><Icon name="pin" size={14} /> {app.t.location}</h2>
    <input class="input" bind:value={q} placeholder={app.t.searchCity} dir="auto" />
    {#if matches.length}
      <ul class="results">
        {#each matches as c}<li><button onclick={() => pickCity(c)}>{app.lang === "ar" ? c.name_ar : c.name} <span class="faint">{c.country}</span></button></li>{/each}
      </ul>
    {/if}
    <div class="two">
      <label class="field">{app.t.latitude}<input class="input" type="number" step="0.0001" bind:value={cfg.location.latitude} onchange={persist} /></label>
      <label class="field">{app.t.longitude}<input class="input" type="number" step="0.0001" bind:value={cfg.location.longitude} onchange={persist} /></label>
      <label class="field">{app.t.timezone}<input class="input" bind:value={cfg.location.timezone} onchange={persist} /></label>
      <label class="field">{app.t.elevation}<input class="input" type="number" step="1" bind:value={cfg.location.elevation_m} onchange={persist} /></label>
    </div>
  </section>

  <section class="glass card">
    <h2>{app.t.method}</h2>
    <label class="field">{app.t.method}
      <select class="input" value={cfg.params.method} onchange={(e) => setMethod(e.currentTarget.value)}>
        {#each app.methods as m}<option value={m.id}>{m.name}</option>{/each}
      </select>
    </label>
    <p class="faint small">
      Fajr {app.fmt.num(cfg.params.fajr_angle)}° ·
      Isha {cfg.params.isha.type === "angle" ? `${app.fmt.num(cfg.params.isha.value)}°` : `${app.fmt.num(cfg.params.isha.value)} ${app.t.minutes}`}
    </p>
    <label class="field">{app.t.madhab}
      <select class="input" bind:value={cfg.params.madhab} onchange={persist}>
        <option value="standard">{app.t.standard}</option><option value="hanafi">{app.t.hanafi}</option>
      </select>
    </label>
    <div class="two">
      <label class="field">{app.t.highLat}
        <select class="input" bind:value={cfg.params.high_latitude_rule} onchange={persist}>
          {#each ["auto", "middle_of_the_night", "seventh_of_the_night", "twilight_angle", "none"] as r}<option value={r}>{app.t[`hl_${r}` as "hl_auto"]}</option>{/each}
        </select>
      </label>
      <label class="field">{app.t.polarRule}
        <select class="input" bind:value={cfg.params.polar_rule} onchange={persist}>
          {#each ["nearest_latitude", "unresolved"] as r}<option value={r}>{app.t[`polar_${r}` as "polar_unresolved"]}</option>{/each}
        </select>
      </label>
    </div>
    <label class="field">{app.t.rounding}
      <select class="input" bind:value={cfg.params.rounding} onchange={persist}>
        {#each ["safe", "nearest", "none"] as r}<option value={r}>{app.t[`round_${r}` as "round_safe"]}</option>{/each}
      </select>
    </label>
    <div class="field">{app.t.adjustments}
      <div class="adj">
        {#each adjKeys as k}
          <div class="adj-row">
            <span>{app.t.keys[k]}</span>
            <button class="step" onclick={() => bump(k, -1)} aria-label="−1">−</button>
            <span class="tabular val">{cfg.params.adjustments[k] > 0 ? "+" : ""}{app.fmt.num(cfg.params.adjustments[k])}</span>
            <button class="step" onclick={() => bump(k, 1)} aria-label="+1">+</button>
          </div>
        {/each}
      </div>
    </div>
    <p class="faint small">{app.t.accuracy}</p>
  </section>

  <section class="glass card">
    <h2>{app.t.hijri}</h2>
    <label class="field">{app.t.hijriCalendar}
      <select class="input" bind:value={cfg.hijri.calendar} onchange={persist}>
        <option value="umm_al_qura">{app.t.ummAlQura}</option><option value="tabular">{app.t.tabular}</option>
      </select>
    </label>
    <div class="field">{app.t.dayAdjust}
      <div class="seg">
        {#each [-2, -1, 0, 1, 2] as d}
          <button class:on={cfg.hijri.day_adjustment === d} onclick={() => { cfg.hijri.day_adjustment = d; persist(); }}>{d > 0 ? "+" : ""}{app.fmt.num(d)}</button>
        {/each}
      </div>
    </div>
    <button class="btn" onclick={refreshFeed}><Icon name="refresh" size={14} /> {app.t.refreshFeed}</button>
    {#if feedMsg}<p class="faint small">{feedMsg}</p>{/if}

    <h2 class="sub">{app.t.protect}</h2>
    <div class="three">
      <label class="field">{app.t.protectBefore}<input class="input" type="number" min="0" max="60" bind:value={cfg.guard.before_minutes} onchange={persist} /></label>
      <label class="field">{app.t.protectAfter}<input class="input" type="number" min="0" max="90" bind:value={cfg.guard.after_minutes} onchange={persist} /></label>
      <label class="field">{app.t.jumuahExtra}<input class="input" type="number" min="0" max="120" bind:value={cfg.guard.jumuah_extra_minutes} onchange={persist} /></label>
    </div>
  </section>

  <section class="glass card">
    <h2><Icon name="bell" size={14} /> {app.t.notifications}</h2>
    <div class="toggles">
      {#each prayerKeys as k, i}
        <label class="tog"><input type="checkbox" bind:checked={prefs.adhan[i]} onchange={persistPrefs} /> {app.t.adhan} · {app.t.keys[k]}</label>
      {/each}
    </div>
    <div class="two">
      <label class="field">{app.t.preReminder}
        <select class="input" bind:value={prefs.pre_reminder_minutes} onchange={persistPrefs}>
          {#each [0, 5, 10, 15, 20, 30] as m}<option value={m}>{m ? `${app.fmt.num(m)} ${app.t.minutes}` : app.t.none}</option>{/each}
        </select>
      </label>
      <label class="field">{app.t.sunriseWarning}
        <select class="input" bind:value={prefs.sunrise_warning_minutes} onchange={persistPrefs}>
          {#each [0, 10, 15, 20, 30] as m}<option value={m}>{m ? `${app.fmt.num(m)} ${app.t.minutes}` : app.t.none}</option>{/each}
        </select>
      </label>
    </div>
    <div class="row">
      <button class="btn" onclick={() => api.testNotification(app.t.appName, app.t.tagline)}>{app.t.test}</button>
      <label class="tog"><input type="checkbox" checked={autostart} onchange={toggleAutostart} /> {app.t.startup}</label>
    </div>

    <h2 class="sub">{app.t.appearance}</h2>
    <div class="three">
      <label class="field">{app.t.language}
        <select class="input" bind:value={prefs.lang} onchange={persistPrefs}>
          <option value="auto">{app.t.auto}</option><option value="ar">العربية</option><option value="en">English</option><option value="fr">Français</option>
        </select>
      </label>
      <label class="field">{app.t.numerals}
        <select class="input" bind:value={prefs.numerals} onchange={persistPrefs}>
          <option value="auto">{app.t.auto}</option><option value="latn">{app.t.latn}</option><option value="arab">{app.t.arab}</option>
        </select>
      </label>
      <label class="field">{app.t.clock}
        <select class="input" bind:value={prefs.clock} onchange={persistPrefs}>
          <option value="24h">24h</option><option value="12h">12h</option>
        </select>
      </label>
    </div>
    <label class="tog"><input type="checkbox" bind:checked={prefs.reduce_motion} onchange={persistPrefs} /> {app.t.reduceMotion}</label>
  </section>
</div>

<style>
  .cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 14px; align-items: start; }
  .cols > section { display: grid; gap: 12px; }
  .sub { margin-top: 10px !important; }
  .small { font-size: 12.5px; margin: 0; }
  .two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
  .results { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
  .results button { width: 100%; text-align: start; padding: 8px 12px; border-radius: 12px; background: var(--glass-strong); border: 0; }
  .adj { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px 16px; color: var(--ink); }
  .adj-row { display: grid; grid-template-columns: 1fr auto 3ch auto; gap: 6px; align-items: center; }
  .val { text-align: center; }
  .step { width: 28px; height: 28px; border-radius: 50%; border: 0; background: var(--glass-strong); }
  .seg { display: flex; background: var(--glass-strong); border-radius: 999px; overflow: hidden; width: max-content; padding: 2px; }
  .seg button { border-radius: 999px; }
  .seg button { background: transparent; border: 0; padding: 8px 14px; color: var(--ink); }
  .seg button.on { background: var(--accent); color: var(--accent-ink); font-weight: 600; }
  .toggles { display: grid; gap: 8px; }
  .tog { display: flex; align-items: center; gap: 10px; color: var(--ink); font-size: 14px; }
  .tog input { width: 18px; height: 18px; accent-color: var(--accent); }
  .row { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
  .btn { display: inline-flex; gap: 6px; align-items: center; justify-self: start; }
</style>

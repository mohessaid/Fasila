<script lang="ts">
  import { api } from "./api";
  import { app } from "./state.svelte";
  import Icon from "./Icon.svelte";
  import type { City } from "./types";

  let q = $state("");
  let manual = $state(false);
  let lat = $state(""), lon = $state(""), tz = $state(Intl.DateTimeFormat().resolvedOptions().timeZone), country = $state("");

  const matches = $derived(
    q.trim().length < 1
      ? app.cities.filter((c) => c.timezone === tz).slice(0, 6)
      : app.cities.filter((c) => (c.name + " " + c.name_ar + " " + c.country).toLowerCase().includes(q.trim().toLowerCase())).slice(0, 8),
  );

  async function pick(c: City) {
    const cfg = await api.defaultConfig(app.lang === "ar" ? c.name_ar : c.name, c.latitude, c.longitude, c.timezone, c.country);
    await app.saveConfig(cfg);
  }
  async function useManual() {
    const la = parseFloat(lat), lo = parseFloat(lon);
    if (!isFinite(la) || !isFinite(lo)) return;
    const cfg = await api.defaultConfig(`${la.toFixed(3)}, ${lo.toFixed(3)}`, la, lo, tz, country);
    await app.saveConfig(cfg);
  }
</script>

<div class="center">
  <section class="glass card onboard">
    <div class="mark" aria-hidden="true"></div>
    <h1>{app.t.welcome}</h1>
    <p class="muted">{app.t.welcomeBody}</p>
    <div class="langs">
      {#each [["en", "English"], ["ar", "العربية"], ["fr", "Français"]] as [code, label]}
        <button class="chip" class:on={app.lang === code} onclick={() => app.savePrefs({ ...app.prefs, lang: code as "en" })}>{label}</button>
      {/each}
    </div>
    {#if !manual}
      <input class="input" bind:value={q} placeholder={app.t.searchCity} dir="auto" />
      <ul class="results">
        {#each matches as c}
          <li><button onclick={() => pick(c)}>
            <Icon name="pin" size={16} />
            <span>{app.lang === "ar" ? c.name_ar : c.name}</span>
            <span class="faint">{c.country} · {c.timezone}</span>
          </button></li>
        {/each}
      </ul>
      <button class="btn ghost" onclick={() => (manual = true)}>{app.t.manual}</button>
    {:else}
      <div class="manual">
        <label class="field">{app.t.latitude}<input class="input" bind:value={lat} inputmode="decimal" placeholder="36.7538" /></label>
        <label class="field">{app.t.longitude}<input class="input" bind:value={lon} inputmode="decimal" placeholder="3.0588" /></label>
        <label class="field">{app.t.timezone}<input class="input" bind:value={tz} /></label>
        <label class="field">{app.t.country}<input class="input" bind:value={country} maxlength="2" placeholder="DZ" /></label>
      </div>
      <div class="row">
        <button class="btn ghost" onclick={() => (manual = false)}>{app.t.cancel}</button>
        <button class="btn primary" onclick={useManual}>{app.t.continue}</button>
      </div>
    {/if}
  </section>
</div>

<style>
  .center { min-height: 100%; display: grid; place-items: center; padding: 20px; }
  .onboard { width: min(480px, 100%); display: grid; gap: 14px; text-align: center; padding: 34px; }
  h1 { margin: 0; font-size: 30px; font-weight: 600; }
  p { margin: 0; }
  .mark { width: 64px; height: 64px; margin: 0 auto; border-radius: 50%; border: 6px solid var(--ink-3); border-top-color: var(--accent); border-inline-end-color: var(--accent); transform: rotate(-20deg); }
  .langs { display: flex; gap: 8px; justify-content: center; }
  .langs .on { background: var(--accent); color: var(--accent-ink); }
  .results { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; max-height: 300px; overflow: auto; }
  .results button { width: 100%; display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; text-align: start; padding: 10px 14px; border-radius: 14px; background: var(--glass); border: 1px solid transparent; }
  .results button:hover { border-color: var(--glass-border); background: var(--glass-strong); }
  .manual { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; text-align: start; }
  .row { display: flex; gap: 8px; justify-content: center; }
</style>

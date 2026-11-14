<script lang="ts">
  import { onMount } from "svelte";
  import { app } from "./lib/state.svelte";
  import Sky from "./lib/Sky.svelte";
  import Today from "./lib/Today.svelte";
  import Schedule from "./lib/Schedule.svelte";
  import Calendar from "./lib/Calendar.svelte";
  import Qibla from "./lib/Qibla.svelte";
  import Settings from "./lib/Settings.svelte";
  import Onboarding from "./lib/Onboarding.svelte";
  import Icon from "./lib/Icon.svelte";

  onMount(() => {
    app.init().catch((e) => console.error(e));
  });

  // Push language, direction and sky into the document root.
  $effect(() => {
    const r = document.documentElement;
    r.lang = app.lang;
    r.dir = app.dir;
    r.dataset.motion = app.prefs.reduce_motion ? "reduce" : "full";
    r.dataset.transparency = app.prefs.solid_panes ? "reduce" : "full";
    app.skyColors.forEach((c, i) => r.style.setProperty(`--sky-${i}`, c));
    r.style.setProperty("--tint-top", String(app.tint.top));
    r.style.setProperty("--tint-mid", String(app.tint.mid));
    r.style.setProperty("--tint-bottom", String(app.tint.bottom));
    document.title = app.t.appName;
  });

  const tabs = [
    { id: "today", icon: "sun" },
    { id: "schedule", icon: "list" },
    { id: "calendar", icon: "calendar" },
    { id: "qibla", icon: "compass" },
    { id: "settings", icon: "gear" },
  ] as const;

  function onKey(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key >= "1" && e.key <= "5") {
      app.tab = tabs[Number(e.key) - 1].id;
      e.preventDefault();
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<Sky />

{#if app.ready}
  {#if !app.config}
    <Onboarding />
  {:else}
    <div class="shell">
      <nav class="nav" aria-label={app.t.appName}>
        <div class="brand">
          <span class="logo" aria-hidden="true"></span>
          <span>{app.t.appName}</span>
        </div>
        <div class="tabs" role="tablist">
          {#each tabs as tab, i}
            <button
              role="tab"
              class:active={app.tab === tab.id}
              class:lit={app.tab === tab.id}
              aria-selected={app.tab === tab.id}
              title={`${app.t[tab.id]} (Ctrl+${i + 1})`}
              onclick={() => (app.tab = tab.id)}
            >
              <Icon name={tab.icon} />
              <span class="tab-text">{app.t[tab.id]}</span>
            </button>
          {/each}
        </div>
        <div class="place muted" title={`${app.config.location.latitude.toFixed(4)}, ${app.config.location.longitude.toFixed(4)}`}>
          <Icon name="pin" size={14} />
          {app.config.location.name || `${app.config.location.latitude.toFixed(2)}, ${app.config.location.longitude.toFixed(2)}`}
        </div>
      </nav>

      <main>
        {#key app.tab}
          <div class="page">
            {#if app.tab === "today"}<Today />
            {:else if app.tab === "schedule"}<Schedule />
            {:else if app.tab === "calendar"}<Calendar />
            {:else if app.tab === "qibla"}<Qibla />
            {:else}<Settings />{/if}
          </div>
        {/key}
      </main>
    </div>
  {/if}
{/if}

{#if !app.ready}
  <!-- Skeleton shaped like Today, shown while the core computes the first days. -->
  <div class="skeleton" aria-busy="true" aria-label="Loading">
    <div class="sk-stage">
      <span class="sk sk-title"></span>
      <span class="sk sk-sub"></span>
      <span class="sk sk-arc"></span>
      <span class="sk sk-count"></span>
    </div>
    <div class="glass sk-panel">
      {#each Array(6) as _}<span class="sk sk-row"></span>{/each}
    </div>
  </div>
{/if}

{#if app.toast}
  <div class="toast glass" role="status">{app.toast}</div>
{/if}

<style>
  .shell { position: relative; height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 8px; padding: 12px 20px 16px; }
  .nav { display: flex; align-items: center; gap: 12px; padding: 6px 10px; }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 17px; letter-spacing: -0.01em; }
  .logo {
    width: 20px; height: 20px; border-radius: 50%;
    border: 2.5px solid var(--ink-3); border-top-color: var(--accent); border-inline-end-color: var(--accent);
    transform: rotate(-20deg);
  }
  .tabs { display: flex; gap: 2px; margin-inline: auto; }
  .tabs button {
    display: flex; align-items: center; gap: 8px; padding: 9px 16px; border-radius: 999px;
    background: transparent; border: 0; color: var(--ink-3); font-weight: 500; font-size: 14.5px;
    transition: color 0.2s, background 0.25s, box-shadow 0.25s;
  }
  .tabs button:hover:not(.active) { color: var(--ink-2); background: rgba(255, 255, 255, 0.05); }
  .tabs button.active { color: var(--ink); font-weight: 600; border-radius: 999px; }
  .place { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--ink-3); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  main { overflow: auto; min-height: 0; border-radius: var(--radius); }
  .page { animation: rise 0.35s ease both; }
  :global(:root[data-motion="reduce"]) .page { animation: none; }
  @keyframes rise { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
  .skeleton { height: 100%; display: grid; grid-template-columns: 1.3fr 1fr; gap: 28px; padding: 76px 24px 24px; max-width: 1180px; margin: 0 auto; }
  .sk-stage { display: grid; justify-items: center; align-content: start; gap: 14px; padding-top: 12px; }
  .sk-panel { padding: 30px 28px; display: grid; gap: 14px; align-content: start; }
  .sk { display: block; border-radius: 10px; background: linear-gradient(90deg, rgba(255,255,255,0.05), rgba(255,255,255,0.12), rgba(255,255,255,0.05)); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }
  .sk-title { width: 46%; height: 26px; }
  .sk-sub { width: 32%; height: 14px; }
  .sk-arc { width: 90%; height: 200px; border-radius: 200px 200px 24px 24px; margin-top: 18px; }
  .sk-count { width: 40%; height: 56px; }
  .sk-row { height: 22px; }
  @keyframes shimmer { to { background-position: -200% 0; } }
  :global(:root[data-motion="reduce"]) .sk { animation: none; }
  .toast { position: fixed; background: var(--veil); inset-block-end: 22px; inset-inline-start: 50%; translate: -50% 0; padding: 10px 20px; border-radius: 999px; font-size: 14px; z-index: 10; }
  :global([dir="rtl"]) .toast { translate: 50% 0; }

  @media (max-width: 820px) {
    .shell { grid-template-rows: 1fr auto; padding: 10px; }
    .nav { order: 2; padding: 4px; border-radius: 22px; background: var(--veil); backdrop-filter: blur(var(--blur)); -webkit-backdrop-filter: blur(var(--blur)); }
    .brand, .place { display: none; }
    .tabs { margin: 0; width: 100%; justify-content: space-around; }
    .tabs button { flex-direction: column; gap: 3px; padding: 8px 10px 10px; font-size: 11px; }
  }
</style>

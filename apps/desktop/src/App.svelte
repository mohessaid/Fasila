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
    app.skyColors.forEach((c, i) => r.style.setProperty(`--sky-${i}`, c));
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
    position: relative; display: flex; align-items: center; gap: 8px; padding: 10px 14px;
    background: transparent; border: 0; color: var(--ink-3); font-weight: 500; font-size: 14.5px;
    transition: color 0.2s;
  }
  .tabs button :global(svg) { opacity: 0.8; }
  .tabs button:hover { color: var(--ink-2); }
  .tabs button.active { color: var(--ink); }
  .tabs button.active::after {
    content: ""; position: absolute; bottom: 2px; inset-inline-start: 50%; translate: -50% 0;
    width: 4px; height: 4px; border-radius: 50%; background: var(--accent);
  }
  :global([dir="rtl"]) .tabs button.active::after { translate: 50% 0; }
  .place { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--ink-3); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  main { overflow: auto; min-height: 0; border-radius: var(--radius); }
  .page { animation: rise 0.35s ease both; }
  :global(:root[data-motion="reduce"]) .page { animation: none; }
  @keyframes rise { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
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

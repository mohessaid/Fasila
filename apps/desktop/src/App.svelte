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
      <nav class="glass nav" aria-label={app.t.appName}>
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
              <span class="label">{app.t[tab.id]}</span>
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
  .shell { position: relative; height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 14px; padding: 14px; }
  .nav {
    display: flex; align-items: center; gap: 12px; padding: 8px 10px 8px 18px;
    border-radius: 999px; padding-inline: 18px 10px;
  }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 17px; letter-spacing: 0.01em; }
  .logo {
    width: 22px; height: 22px; border-radius: 50%;
    border: 3px solid var(--ink-3); border-top-color: var(--accent); border-inline-end-color: var(--accent);
    transform: rotate(-20deg);
  }
  .tabs { display: flex; gap: 4px; margin-inline: auto; }
  .tabs button {
    display: flex; align-items: center; gap: 8px; padding: 9px 16px; border-radius: 999px;
    background: transparent; border: 1px solid transparent; color: var(--ink-2); font-weight: 500;
    transition: background 0.2s, color 0.2s;
  }
  .tabs button:hover { color: var(--ink); background: var(--glass); }
  .tabs button.active { color: var(--ink); background: var(--glass-strong); border-color: var(--glass-border); }
  .place { display: flex; align-items: center; gap: 6px; font-size: 13px; padding-inline-end: 8px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  main { overflow: auto; min-height: 0; border-radius: var(--radius); }
  .page { animation: rise 0.35s ease both; }
  :global(:root[data-motion="reduce"]) .page { animation: none; }
  @keyframes rise { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
  .toast { position: fixed; inset-block-end: 22px; inset-inline-start: 50%; translate: -50% 0; padding: 10px 20px; border-radius: 999px; font-size: 14px; z-index: 10; }
  :global([dir="rtl"]) .toast { translate: 50% 0; }

  @media (max-width: 820px) {
    .shell { grid-template-rows: 1fr auto; padding: 10px; }
    .nav { order: 2; padding: 6px; border-radius: 26px; }
    .brand, .place { display: none; }
    .tabs { margin: 0; width: 100%; justify-content: space-around; }
    .tabs button { flex-direction: column; gap: 2px; padding: 8px 10px; font-size: 11px; border-radius: 20px; }
  }
</style>

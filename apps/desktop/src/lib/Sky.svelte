<script lang="ts">
  import { app } from "./state.svelte";

  // Sun (or moon) glow travels across the sky with the day's progress.
  const progress = $derived.by(() => {
    const t = app.todayDay?.times;
    if (!t?.sunrise || !t?.sunset) return 0.5;
    return Math.min(1, Math.max(0, (app.now - t.sunrise) / (t.sunset - t.sunrise)));
  });
  const isNight = $derived(app.period === "night" || app.period === "fajr");
  const x = $derived(app.dir === "rtl" ? 90 - progress * 80 : 10 + progress * 80);
  const y = $derived(isNight ? 18 : 70 - Math.sin(progress * Math.PI) * 55);
</script>

<div class="sky" aria-hidden="true">
  <div class="stars" class:on={isNight}></div>
  <div class="orb" class:moon={isNight} style:left="{x}%" style:top="{y}%"></div>
  <div class="haze"></div>
</div>

<style>
  .sky { position: fixed; inset: 0; overflow: hidden; z-index: -1; pointer-events: none; }
  .orb {
    position: absolute; width: 46vmax; height: 46vmax; translate: -50% -50%; border-radius: 50%;
    background: radial-gradient(circle, color-mix(in srgb, var(--accent) 70%, white) 0%, color-mix(in srgb, var(--accent) 30%, transparent) 22%, transparent 62%);
    opacity: 0.55; filter: blur(8px);
    transition: left 60s linear, top 60s linear, opacity 4s;
  }
  .orb.moon { opacity: 0.22; width: 30vmax; height: 30vmax; }
  .haze {
    position: absolute; inset: auto -10% -30% -10%; height: 60%;
    background: radial-gradient(ellipse at 50% 100%, color-mix(in srgb, var(--sky-bottom) 80%, white 20%), transparent 70%);
    opacity: 0.5;
  }
  .stars {
    position: absolute; inset: 0; opacity: 0; transition: opacity 4s;
    background-image:
      radial-gradient(1.2px 1.2px at 12% 18%, #fff, transparent), radial-gradient(1px 1px at 28% 42%, #fff, transparent),
      radial-gradient(1.4px 1.4px at 44% 12%, #fff, transparent), radial-gradient(1px 1px at 63% 30%, #fff, transparent),
      radial-gradient(1.2px 1.2px at 78% 8%, #fff, transparent), radial-gradient(1px 1px at 88% 36%, #fff, transparent),
      radial-gradient(1px 1px at 7% 55%, #fff, transparent), radial-gradient(1.3px 1.3px at 35% 68%, #fff, transparent),
      radial-gradient(1px 1px at 55% 52%, #fff, transparent), radial-gradient(1.1px 1.1px at 92% 62%, #fff, transparent),
      radial-gradient(1px 1px at 70% 78%, #fff, transparent), radial-gradient(1.2px 1.2px at 20% 85%, #fff, transparent);
  }
  .stars.on { opacity: 0.7; }
  :global(:root[data-motion="reduce"]) .orb { transition: none; }
</style>

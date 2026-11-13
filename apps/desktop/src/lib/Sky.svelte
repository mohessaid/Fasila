<script lang="ts">
  import { app } from "./state.svelte";

  // 45 stars, each with its own size, place and twinkle rhythm (upper 80 % of the sky).
  const stars = Array.from({ length: 45 }, () => ({
    x: Math.random() * 100,
    y: Math.random() * 80,
    size: 0.5 + Math.random() * 2,
    delay: Math.random() * 3,
    duration: 2 + Math.random() * 4,
    max: 0.28 + Math.random() * 0.62,
  }));
</script>

<div class="sky" aria-hidden="true">
  <div class="stars" style:opacity={app.stars}>
    {#each stars as s}
      <span
        style:left="{s.x}%"
        style:top="{s.y}%"
        style:width="{s.size}px"
        style:height="{s.size}px"
        style:--max={s.max}
        style:animation-delay="{s.delay}s"
        style:animation-duration="{s.duration}s"
      ></span>
    {/each}
  </div>
</div>

<style>
  .sky { position: fixed; inset: 0; overflow: hidden; z-index: -1; pointer-events: none; }
  .stars { position: absolute; inset: 0; transition: opacity 1.5s ease; }
  .stars span {
    position: absolute; border-radius: 50%; background: #fff;
    opacity: calc(var(--max) * 0.58);
    animation: twinkle ease-in-out infinite alternate;
  }
  @keyframes twinkle { to { opacity: var(--max); } }
  :global(:root[data-motion="reduce"]) .stars span { animation: none; opacity: var(--max); }
</style>

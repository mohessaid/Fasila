<script lang="ts">
  // An editable word inside a sentence: shows only the chosen label (so it sizes to its
  // text), with a transparent native <select> on top for accessible, platform-native picking.
  let {
    value,
    options,
    onchange,
    label,
    tone = "normal",
  }: {
    value: string;
    options: { value: string; label: string }[];
    onchange: (v: string) => void;
    label?: string;
    tone?: "normal" | "strong" | "quiet";
  } = $props();
  const current = $derived(options.find((o) => o.value === value)?.label ?? value);
</script>

<span class="tok {tone}">
  <span class="text" aria-hidden="true">{current}</span>
  <select {value} aria-label={label} onchange={(e) => onchange(e.currentTarget.value)}>
    {#each options as o}<option value={o.value}>{o.label}</option>{/each}
  </select>
</span>

<style>
  .tok {
    position: relative; display: inline-block; white-space: nowrap; border-radius: 999px;
    background: rgba(255, 255, 255, 0.08); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
    transition: background 0.2s, box-shadow 0.2s;
  }
  .text { display: inline-block; padding: 3px 12px; color: var(--ink); font-weight: 600; }
  .tok:hover { background: rgba(255, 255, 255, 0.14); }
  /* The prayer anchor is the lit word of the sentence. */
  .strong {
    background: radial-gradient(120% 160% at 50% -20%, rgba(255, 155, 84, 0.35), transparent 70%), rgba(255, 255, 255, 0.14);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3), 0 6px 16px rgba(255, 155, 84, 0.18);
  }
  .quiet { background: rgba(255, 255, 255, 0.04); }
  .quiet .text { color: var(--ink-2); font-weight: 500; }
  .tok:focus-within {
    background: rgba(255, 255, 255, 0.2);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.36), 0 8px 20px rgba(255, 155, 84, 0.22);
  }
  select { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; font: inherit; appearance: none; -webkit-appearance: none; }
  select option { color: #111; background: #fff; }
</style>

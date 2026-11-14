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
  .tok { position: relative; display: inline-block; white-space: nowrap; }
  .text {
    color: var(--ink); font-weight: 600;
    text-decoration: underline; text-decoration-color: color-mix(in srgb, var(--accent) 70%, transparent);
    text-decoration-thickness: 1.5px; text-underline-offset: 6px;
  }
  .strong .text { color: var(--accent); }
  .quiet .text { color: var(--ink-2); font-weight: 500; text-decoration-color: var(--hairline); }
  .tok:hover .text { color: var(--accent); }
  .tok:focus-within .text { color: var(--accent); outline: 2px solid color-mix(in srgb, var(--accent) 45%, transparent); outline-offset: 3px; border-radius: 4px; }
  select { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; font: inherit; appearance: none; -webkit-appearance: none; }
  select option { color: #111; background: #fff; }
</style>

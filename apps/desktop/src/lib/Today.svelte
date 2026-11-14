<script lang="ts">
  import { app } from "./state.svelte";
  import CelestialHero from "./CelestialHero.svelte";
  import Icon from "./Icon.svelte";
  import type { TimeKey } from "./types";

  const day = $derived(app.todayDay);
  const rows: TimeKey[] = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"];
  const extras: TimeKey[] = ["imsak", "midnight", "last_third"];

  function estimated(k: TimeKey) {
    if (!day) return false;
    return (k === "fajr" && day.times.fajr_estimated) || (k === "isha" && day.times.isha_estimated);
  }
</script>

{#if day}
  <div class="today">
    <section class="stage">
      <header class="date">
        <h1>
          {app.fmt.num(day.hijri.day)} {app.t.hijriMonths[day.hijri.month - 1]}
          <span class="year">{app.fmt.num(day.hijri.year)}</span>
        </h1>
        <p>{app.fmt.longDate(day.date)}</p>
        {#if day.occasions.length}
          <p class="occ">{day.occasions.map((o) => app.t.occ[o] ?? o).join(", ")}</p>
        {/if}
      </header>
      <CelestialHero {day} />
      {#if day.times.polar_fallback}
        <p class="note"><Icon name="info" size={14} /> {app.t.polar}</p>
      {/if}
    </section>

    <aside class="glass panel">
      <div class="label">{app.t.today}</div>
      <ol class="times">
        {#each rows as k}
          {@const at = day.times[k]}
          {@const isNext = app.next?.key === k && app.next.day.date === day.date}
          {@const isCur = app.current?.key === k && at !== null && app.current.at === at}
          <li class:next={isNext} class:lit={isNext} class:pane={isCur && !isNext} class:cur={isCur} class:minor={k === "sunrise"} class:past={at !== null && at < app.now && !isCur}>
            <span class="name">
              {app.keyName(k, day)}
              {#if estimated(k)}<abbr class="est" title={app.t.estimatedHelp}>{app.t.estimated}</abbr>{/if}
            </span>
            <span class="time tabular">{app.fmt.time(at)}</span>
            {#if isNext}
              <span class="sub tabular">{app.t.in} {app.fmt.countdown(at! - app.now)}</span>
            {:else if isCur}
              <span class="sub">{app.t.now}</span>
            {/if}
          </li>
        {/each}
      </ol>
      <dl class="extras">
        {#each extras as k}
          <div><dt>{app.t.keys[k]}</dt><dd class="tabular">{app.fmt.time(day.times[k])}</dd></div>
        {/each}
      </dl>

      <div class="label plan-label">{app.t.todaysPlan}</div>
      {#if app.todayOcc.length === 0}
        <p class="empty">{app.t.nothingPlanned}</p>
        <button class="btn" onclick={() => (app.tab = "schedule")}><Icon name="plus" size={15} /> {app.t.add}</button>
      {:else}
        <ul class="plan">
          {#each app.todayOcc as o}
            <li class="frost" class:done={(o.end ?? o.start) < app.now}>
              <span class="when tabular">{app.fmt.time(o.start)}</span>
              <span class="what">
                {o.title || app.t[o.kind]}
                {#if o.end}<span class="until tabular"> {app.t.rangeTo} {app.fmt.time(o.end)}</span>{/if}
                {#if o.conflicts.length}
                  <span class="clash">{app.t.conflict} {o.conflicts.map((c) => app.t.keys[c.prayer]).join(", ")}</span>
                {/if}
              </span>
            </li>
          {/each}
        </ul>
      {/if}
    </aside>
  </div>
{/if}

<style>
  .today {
    display: grid; gap: 28px; align-items: start;
    grid-template-columns: minmax(340px, 1.3fr) minmax(300px, 1fr);
    max-width: 1180px; margin: 0 auto; padding: 8px 4px 20px;
  }
  .stage { display: grid; gap: 4px; padding-top: 12px; }
  .date { text-align: center; }
  h1 { margin: 0; font-size: 26px; font-weight: 600; letter-spacing: -0.02em; }
  :global(:root[lang="ar"]) h1 { letter-spacing: 0; }
  .year { color: var(--ink-3); font-weight: 400; }
  .date p { margin: 4px 0 0; color: var(--ink-2); font-size: 14px; }
  .date .occ { color: var(--accent); font-size: 13px; font-weight: 500; }
  .note { display: flex; gap: 8px; align-items: center; justify-content: center; color: var(--warn); font-size: 13px; }

  .panel { padding: 26px 28px; }
  .times { list-style: none; margin: 0 -14px; padding: 0; display: grid; gap: 2px; }
  .times li {
    position: relative; display: grid; grid-template-columns: 1fr auto; align-items: baseline;
    padding: 11px 14px; column-gap: 12px; border-radius: var(--radius-sm);
    transition: background 0.4s ease, box-shadow 0.4s ease;
  }
  .name { font-size: 17px; font-weight: 500; display: flex; align-items: baseline; gap: 8px; }
  .time { font-size: 18px; font-weight: 400; color: var(--ink-2); }
  .sub { grid-column: 1 / -1; font-size: 12.5px; color: var(--ink-3); margin-top: 2px; }
  .minor .name, .minor .time { font-size: 14px; color: var(--ink-3); }
  .past:not(.minor) .name, .past:not(.minor) .time { color: var(--ink-3); }
  .next { padding-block: 14px; }
  .next .name { font-size: 19px; font-weight: 600; }
  .next .time { color: var(--ink); font-size: 20px; font-weight: 600; }
  .next .sub { color: var(--ink-2); }
  .est { font-size: 11px; color: var(--warn); text-decoration: none; font-weight: 500; }

  .extras { display: flex; justify-content: space-between; margin: 16px 0 0; font-size: 12.5px; }
  .extras div { display: grid; gap: 3px; }
  .extras dt { color: var(--ink-3); }
  .extras dd { margin: 0; color: var(--ink-2); }

  .plan-label { margin-top: 30px; }
  .empty { margin: 0 0 14px; color: var(--ink-2); font-size: 14px; line-height: 1.5; }
  .plan { list-style: none; margin: 0 -14px; padding: 0; display: grid; gap: 2px; }
  .plan li { display: grid; grid-template-columns: 52px 1fr; gap: 14px; align-items: baseline; padding: 9px 14px; }
  .when { color: var(--ink-3); font-size: 13.5px; }
  .what { font-size: 15px; line-height: 1.35; }
  .what .until { color: var(--ink-3); font-size: 13px; font-weight: 400; }
  .clash { display: block; color: var(--warn); font-size: 12.5px; margin-top: 2px; }
  .done { opacity: 0.45; }

  @media (max-width: 860px) {
    .today { grid-template-columns: 1fr; }
  }
</style>

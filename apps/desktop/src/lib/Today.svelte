<script lang="ts">
  import { app } from "./state.svelte";
  import DayDial from "./DayDial.svelte";
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
  <div class="grid">
    <section class="glass card hero">
      <header class="date">
        <div class="hijri">
          {app.fmt.num(day.hijri.day)} {app.t.hijriMonths[day.hijri.month - 1]} {app.fmt.num(day.hijri.year)}
          <span class="faint">{app.t.hijriShort}</span>
        </div>
        <div class="muted">{app.fmt.longDate(day.date)}</div>
        {#if day.occasions.length}
          <div class="occ">
            {#each day.occasions as o}<span class="chip"><Icon name="sparkle" size={14} />{app.t.occ[o] ?? o}</span>{/each}
          </div>
        {/if}
      </header>
      <DayDial {day} />
      {#if day.times.polar_fallback}
        <p class="warn"><Icon name="info" size={14} /> {app.t.polar}</p>
      {/if}
    </section>

    <section class="glass card">
      <h2>{app.t.today}</h2>
      <ol class="prayers">
        {#each rows as k}
          {@const at = day.times[k]}
          {@const isNext = app.next?.key === k && app.next.day.date === day.date}
          {@const isCur = app.current?.key === k && at !== null && app.current.at === at}
          <li class:next={isNext} class:cur={isCur} class:minor={k === "sunrise"} class:past={at !== null && at < app.now && !isCur}>
            <span class="name">
              {app.keyName(k, day)}
              {#if estimated(k)}<span class="badge" title={app.t.estimatedHelp}>{app.t.estimated}</span>{/if}
            </span>
            <span class="time tabular">{app.fmt.time(at)}</span>
            {#if isNext}
              <span class="in tabular">{app.t.in} {app.fmt.countdown(at! - app.now)}</span>
            {:else if isCur}
              <span class="in">{app.t.now}</span>
            {/if}
          </li>
        {/each}
      </ol>
      <div class="extras">
        {#each extras as k}
          <div><span class="faint">{app.t.keys[k]}</span><span class="tabular">{app.fmt.time(day.times[k])}</span></div>
        {/each}
      </div>
    </section>

    <section class="glass card plan">
      <h2>{app.t.todaysPlan}</h2>
      {#if app.todayOcc.length === 0}
        <p class="muted empty">{app.t.nothingPlanned}</p>
        <button class="btn" onclick={() => (app.tab = "schedule")}><Icon name="plus" size={16} /> {app.t.add}</button>
      {:else}
        <ul class="occ-list">
          {#each app.todayOcc as o}
            <li class:done={(o.end ?? o.start) < app.now}>
              <span class="kind"><Icon name={o.kind} size={16} /></span>
              <span class="title">{o.title || app.t[o.kind]}</span>
              <span class="tabular muted">{app.fmt.time(o.start)}{#if o.end}–{app.fmt.time(o.end)}{/if}</span>
              {#if o.conflicts.length}
                <span class="badge" title={app.t.conflictLong}>
                  <Icon name="warn" size={12} /> {o.conflicts.map((c) => app.t.keys[c.prayer]).join(", ")}
                </span>
              {/if}
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  </div>
{/if}

<style>
  .grid {
    display: grid; gap: 14px;
    grid-template-columns: minmax(320px, 1.15fr) minmax(280px, 1fr);
    grid-template-areas: "hero prayers" "hero plan";
    align-items: start;
  }
  .hero { grid-area: hero; display: grid; gap: 10px; }
  .grid > section:nth-child(2) { grid-area: prayers; }
  .plan { grid-area: plan; }
  .date { text-align: center; }
  .hijri { font-size: 22px; font-weight: 600; }
  .occ { display: flex; gap: 8px; justify-content: center; margin-top: 10px; flex-wrap: wrap; }
  .prayers { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
  .prayers li {
    display: grid; grid-template-columns: 1fr auto; align-items: center; column-gap: 12px;
    padding: 12px 14px; border-radius: var(--radius-sm); border: 1px solid transparent;
    transition: background 0.3s;
  }
  .prayers li .name { font-size: 17px; font-weight: 500; display: flex; align-items: center; gap: 8px; }
  .prayers li .time { font-size: 19px; font-weight: 500; }
  .prayers li .in { grid-column: 1 / -1; font-size: 12.5px; color: var(--ink-2); }
  .prayers li.minor .name, .prayers li.minor .time { font-size: 14px; color: var(--ink-2); }
  .prayers li.past:not(.minor) { opacity: 0.6; }
  .prayers li.next { background: var(--glass-strong); border-color: var(--glass-border); box-shadow: inset 3px 0 0 var(--accent); }
  :global([dir="rtl"]) .prayers li.next { box-shadow: inset -3px 0 0 var(--accent); }
  .prayers li.cur { background: var(--glass); }
  .extras { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--glass-border); font-size: 13px; }
  .extras div { display: grid; gap: 2px; text-align: center; }
  .empty { margin: 0 0 12px; }
  .occ-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
  .occ-list li { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding: 10px 12px; border-radius: 14px; background: var(--glass); }
  .occ-list li .badge { grid-column: 2 / -1; justify-self: start; display: inline-flex; gap: 4px; align-items: center; }
  .occ-list li.done { opacity: 0.55; }
  .kind { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 10px; background: var(--glass-strong); }
  .title { font-weight: 500; }
  .warn { display: flex; gap: 8px; align-items: center; margin: 0; }
  @media (max-width: 820px) {
    .grid { grid-template-columns: 1fr; grid-template-areas: "hero" "prayers" "plan"; }
  }
</style>

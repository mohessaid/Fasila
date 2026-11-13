#!/usr/bin/env python3
"""Cross-check fasila-core against independent references.

1. Astropy (ERFA, arcsecond-level solar ephemeris): for every computed event we
   evaluate the *actual* geometric solar altitude at that instant and convert
   the altitude error into a time error. This is ground truth for astronomy.
2. adhanpy (independent implementation of the same methods): minute-level
   agreement check for methods, madhab and high-latitude rules.

usage: python verify.py path/to/dump-binary
Requires: pip install astropy adhanpy
"""
import json, math, subprocess, sys
from datetime import datetime, timezone, timedelta, date as ddate

import numpy as np
from astropy.utils import iers
iers.conf.auto_download = False
iers.conf.iers_degraded_accuracy = "ignore"
from astropy.time import Time
from astropy.coordinates import EarthLocation, AltAz, get_sun
import astropy.units as u

DUMP = sys.argv[1]

CASES = [
    # name, method, madhab, lat, lon, adhan method name
    ("Makkah", "umm_al_qura", "standard", 21.422487, 39.826206, "UMM_AL_QURA"),
    ("Algiers", "muslim_world_league", "standard", 36.7538, 3.0588, "MUSLIM_WORLD_LEAGUE"),
    ("Cairo", "egypt", "standard", 30.0444, 31.2357, "EGYPTIAN"),
    ("Karachi", "karachi", "hanafi", 24.8607, 67.0011, "KARACHI"),
    ("New York", "isna", "standard", 40.7128, -74.0060, "NORTH_AMERICA"),
    ("Jakarta", "singapore", "standard", -6.2088, 106.8456, "SINGAPORE"),
    ("Sydney", "muslim_world_league", "standard", -33.8688, 151.2093, "MUSLIM_WORLD_LEAGUE"),
    ("Dubai", "dubai", "standard", 25.2048, 55.2708, "DUBAI"),
    ("Doha", "qatar", "standard", 25.2854, 51.5310, "QATAR"),
    ("Kuwait", "kuwait", "standard", 29.3759, 47.9774, "KUWAIT"),
    ("Istanbul", "turkey", "standard", 41.0082, 28.9784, None),
    ("London", "moonsighting_committee", "standard", 51.5074, -0.1278, "MOON_SIGHTING_COMMITTEE"),
    ("Tehran", "tehran", "standard", 35.6892, 51.3890, None),
]
DATES = ["2024-03-20", "2024-06-21", "2025-09-23", "2026-12-21", "2026-11-13"]

def sun_alt(ts_list, lat, lon):
    loc = EarthLocation(lat=lat * u.deg, lon=lon * u.deg, height=0 * u.m)
    t = Time(np.array(ts_list, dtype=float), format="unix", scale="utc")
    aa = get_sun(t).transform_to(AltAz(obstime=t, location=loc, pressure=0 * u.hPa))
    return aa.alt.deg

def astro_errors(t, lat, lon, fajr_angle, isha_angle, horizon=-0.8333):
    """Seconds of error for each angle-defined event (altitude error / altitude rate)."""
    checks = {"sunrise": horizon, "sunset": horizon}
    if fajr_angle is not None and not t["fajr_estimated"]:
        checks["fajr"] = -fajr_angle
    if isha_angle is not None and not t["isha_estimated"]:
        checks["isha"] = -isha_angle
    out = {}
    for k, target in checks.items():
        ts = t[k]
        a0, a1 = sun_alt([ts - 30, ts + 30], lat, lon)
        rate = (a1 - a0) / 60.0  # deg/s
        a = sun_alt([ts], lat, lon)[0]
        out[k] = (a - target) / rate if rate else float("nan")
        out[k] = -out[k]
    return out

def sun_hour_angle(ts_list, lon):
    t = Time(np.array(ts_list, dtype=float), format="unix", scale="utc")
    from astropy.coordinates import TETE
    s = get_sun(t).transform_to(TETE(obstime=t))
    lst = t.sidereal_time("apparent", longitude=lon * u.deg).deg
    return ((lst - s.ra.deg + 180) % 360) - 180

def transit_error(ts, lat, lon):
    h = sun_hour_angle([ts], lon)[0]
    return -h / (360.985647 / 86400)  # seconds; positive = true transit later than computed

def run_dump(method, madhab, lat, lon, d):
    out = subprocess.check_output([DUMP, method, madhab, str(lat), str(lon), d]).decode()
    return json.loads(out.strip())

def adhan_times(method_name, madhab, lat, lon, d):
    from adhanpy.PrayerTimes import PrayerTimes
    from adhanpy.calculation import CalculationMethod
    from adhanpy.calculation.CalculationParameters import CalculationParameters
    from adhanpy.calculation.Madhab import Madhab
    m = getattr(CalculationMethod, method_name)
    y, mo, da = map(int, d.split("-"))
    cp = CalculationParameters(method=m)
    cp.madhab = Madhab.HANAFI if madhab == "hanafi" else Madhab.SHAFI
    pt = PrayerTimes((lat, lon), datetime(y, mo, da), calculation_parameters=cp, time_zone=timezone.utc)
    return {k: getattr(pt, k).timestamp() for k in ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")}

worst_astro = 0.0
worst_adhan = 0.0
rows = []
for name, method, madhab, lat, lon, adhan_name in CASES:
    from_params = {
        "umm_al_qura": (18.5, None), "muslim_world_league": (18, 17), "egypt": (19.5, 17.5),
        "karachi": (18, 18), "isna": (15, 15), "singapore": (20, 18), "dubai": (18.2, 18.2),
        "qatar": (18, None), "kuwait": (18, 17.5), "turkey": (18, 17), "moonsighting_committee": (None, None),
        "tehran": (17.7, 14),
    }[method]
    for d in DATES:
        t = run_dump(method, madhab, lat, lon, d)
        errs = astro_errors(t, lat, lon, *from_params)
        # Asr: altitude must equal acot(f + cot(noon altitude)).
        f = 2.0 if madhab == "hanafi" else 1.0
        noon_alt = sun_alt([t["dhuhr"]], lat, lon)[0] if method not in ("turkey", "moonsighting_committee") else None
        if noon_alt is not None:
            target = math.degrees(math.atan(1.0 / (f + 1.0 / math.tan(math.radians(noon_alt)))))
            a0, a1 = sun_alt([t["asr"] - 30, t["asr"] + 30], lat, lon)
            errs["asr"] = -(sun_alt([t["asr"]], lat, lon)[0] - target) / ((a1 - a0) / 60.0)
        errs["dhuhr"] = transit_error(t["dhuhr"], lat, lon) if method not in ("turkey", "moonsighting_committee") else 0.0
        for k, v in errs.items():
            if not (k == "sunrise" and method == "turkey"):  # Diyanet temkin is intentional
                worst_astro = max(worst_astro, abs(v))
        try:
            if adhan_name is None:
                raise RuntimeError("method not in adhanpy")
            a = adhan_times(adhan_name, madhab, lat, lon, d)
            diffs = {k: (t[k] - a[k]) / 60.0 for k in a}
            if method == "umm_al_qura" and d == "2024-03-20":
                # Ramadan: Umm al-Qura Isha is 120 min after Maghrib; adhanpy does not implement it.
                diffs.pop("isha")
            # adhanpy rounds to the nearest minute, so up to 0.5 min is expected noise.
            worst_adhan = max(worst_adhan, max(abs(v) for v in diffs.values()))
        except Exception as e:  # noqa
            diffs = {"error": str(e)}
        rows.append((name, d, {k: round(float(v), 1) for k, v in errs.items()}, {k: round(v, 2) if isinstance(v, float) else v for k, v in diffs.items()}))

for r in rows:
    print(f"{r[0]:10s} {r[1]}  astro err (s): {r[2]}\n{'':21s} vs adhan (min): {r[3]}")
print(f"\nWORST astronomical error: {worst_astro:.1f} s")
print(f"WORST difference vs adhan: {worst_adhan:.2f} min")

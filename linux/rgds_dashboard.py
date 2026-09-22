#!/usr/bin/env python3
"""RGDS Linux prototype. GPL-3.0-or-later. Python 3.8+, no root or pip required."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import time
import uuid


class Reader:
    def __init__(self, proc=Path('/proc'), sys=Path('/sys')):
        self.proc, self.sys, self.previous = proc, sys, None

    def sample(self):
        data = {'schema': 1, 'time': datetime.now(timezone.utc).isoformat(), 'platform': 'linux',
                'version': '0.4.0-prototype', 'cpu_percent': None, 'ram_used_bytes': None,
                'ram_total_bytes': None, 'battery_percent': None, 'thermal_celsius': None,
                'thermal_source': None, 'fps': None, 'reasons': {'fps': 'No Linux frame provider implemented'}}
        try:
            line = (self.proc / 'stat').read_text().splitlines()[0].split()
            if line[0] != 'cpu' or len(line) < 5:
                raise ValueError('Invalid aggregate CPU row')
            values = [int(value) for value in line[1:9]]
            total, idle = sum(values), values[3] + (values[4] if len(values) > 4 else 0)
            if self.previous:
                delta, idle_delta = total - self.previous[0], idle - self.previous[1]
                if delta > 0 and 0 <= idle_delta <= delta:
                    data['cpu_percent'] = round(100 * (delta - idle_delta) / delta, 1)
            if data['cpu_percent'] is None:
                data['reasons']['cpu_percent'] = 'Waiting for two valid samples'
            self.previous = total, idle
        except (OSError, ValueError, IndexError) as error:
            self.previous = None
            data['reasons']['cpu_percent'] = type(error).__name__
        try:
            mem = dict((line.split(':')[0], int(line.split()[1]) * 1024)
                       for line in (self.proc / 'meminfo').read_text().splitlines() if ':' in line)
            total, available = mem['MemTotal'], mem['MemAvailable']
            if not 0 <= available <= total:
                raise ValueError('Invalid memory counters')
            data['ram_total_bytes'], data['ram_used_bytes'] = total, total - available
        except (OSError, ValueError, KeyError, IndexError) as error:
            data['reasons']['ram_used_bytes'] = type(error).__name__
        for battery in sorted((self.sys / 'class/power_supply').glob('*')):
            try:
                if (battery / 'type').read_text().strip() != 'Battery':
                    continue
                value = int((battery / 'capacity').read_text())
                if 0 <= value <= 100:
                    data['battery_percent'] = value
                    break
            except (OSError, ValueError):
                continue
        if data['battery_percent'] is None:
            data['reasons']['battery_percent'] = 'No readable battery capacity'
        for zone in sorted((self.sys / 'class/thermal').glob('thermal_zone*')):
            try:
                value = int((zone / 'temp').read_text()) / 1000
                if -40 <= value <= 150 and (data['thermal_celsius'] is None or value > data['thermal_celsius']):
                    data['thermal_celsius'] = value
                    try:
                        data['thermal_source'] = (zone / 'type').read_text().strip()
                    except OSError:
                        data['thermal_source'] = zone.name
            except (OSError, ValueError):
                continue
        if data['thermal_celsius'] is None:
            data['reasons']['thermal_celsius'] = 'No readable thermal zone'
        return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gui', action='store_true', help='Optional Tk panel')
    parser.add_argument('--geometry', default='640x400+0+0', help='Position on a free display, e.g. 640x400+1920+0')
    parser.add_argument('--samples', type=int, default=0, help='Stop after N samples; zero runs until Ctrl-C')
    parser.add_argument('--log-dir', type=Path, default=Path.home() / '.local/state/rgds-dashboard')
    args = parser.parse_args()
    args.log_dir.mkdir(parents=True, exist_ok=True)
    old = sorted(args.log_dir.glob('rgds-*.jsonl'), key=lambda p: p.stat().st_mtime)
    for path in old[:-9]:
        path.unlink()
    path = args.log_dir / ('rgds-' + str(uuid.uuid4()) + '.jsonl')
    reader, count = Reader(), 0

    def sample():
        nonlocal count
        data = reader.sample()
        line = json.dumps(data, ensure_ascii=False)
        if not path.exists() or path.stat().st_size < 2 * 1024 * 1024:
            with path.open('a', encoding='utf-8') as stream:
                stream.write(line + '\n')
        count += 1
        return data

    try:
        if args.gui:
            try:
                import tkinter as tk
            except ImportError as error:
                raise SystemExit('Tk unavailable; run without --gui: ' + str(error))
            try:
                window = tk.Tk()
            except tk.TclError as error:
                raise SystemExit('Graphical session unavailable; run without --gui: ' + str(error))
            window.title('RGDS Dashboard · Linux prototype')
            window.geometry(args.geometry)
            window.configure(bg='#0c1320')
            label = tk.Label(window, bg='#0c1320', fg='#62e5cb', font=('sans', 17), justify='left')
            label.pack(expand=True, fill='both', padx=20, pady=20)

            def tick():
                data = sample()
                label.config(text='RGDS / LINUX PROTOTYPE\n\n' + '\n'.join(
                    f'{key}: {data[key] if data[key] is not None else "--"}'
                    for key in ['cpu_percent', 'ram_used_bytes', 'battery_percent', 'thermal_celsius', 'thermal_source', 'fps']))
                if args.samples and count >= args.samples:
                    window.destroy()
                else:
                    window.after(1000, tick)
            tick()
            window.mainloop()
        else:
            while not args.samples or count < args.samples:
                print(json.dumps(sample(), ensure_ascii=False), flush=True)
                if not args.samples or count < args.samples:
                    time.sleep(1)
    except KeyboardInterrupt:
        pass
    print('Log: ' + str(path))


if __name__ == '__main__':
    main()

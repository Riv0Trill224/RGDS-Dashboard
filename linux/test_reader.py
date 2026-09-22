import tempfile
from pathlib import Path
import unittest
from rgds_dashboard import Reader


class ReaderTest(unittest.TestCase):
    def test_missing_sources_are_explicit(self):
        with tempfile.TemporaryDirectory() as temp:
            sample = Reader(Path(temp), Path(temp)).sample()
            self.assertIsNone(sample['cpu_percent'])
            self.assertIn('cpu_percent', sample['reasons'])
            self.assertIsNone(sample['fps'])

    def test_cpu_delta_and_actual_sensor_name(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'stat').write_text('cpu 10 0 10 80 0 0 0 0\n')
            (root / 'meminfo').write_text('MemTotal: 1000 kB\nMemAvailable: 400 kB\n')
            zone = root / 'class/thermal/thermal_zone2'
            zone.mkdir(parents=True)
            (zone / 'temp').write_text('43000\n')
            (zone / 'type').write_text('soc-sensor\n')
            reader = Reader(root, root)
            self.assertIsNone(reader.sample()['cpu_percent'])
            (root / 'stat').write_text('cpu 20 0 20 160 0 0 0 0\n')
            sample = reader.sample()
            self.assertEqual(sample['cpu_percent'], 20)
            self.assertEqual(sample['ram_used_bytes'], 600 * 1024)
            self.assertEqual(sample['thermal_celsius'], 43)
            self.assertEqual(sample['thermal_source'], 'soc-sensor')


if __name__ == '__main__':
    unittest.main()

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('profile_ui', ROOT / 'scripts/profile-ui.py')
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


class ProfileTests(unittest.TestCase):
    def test_deadlines_handle_high_refresh_rate_and_invalid_frames(self):
        fixture = '''Flags,IntendedVsync,FrameCompleted,FrameDeadline
0,100000000,109000000,108333333
0,200000000,207000000,208333333
1,300000000,330000000,308333333
0,0,1,2
0,500000000,9223372036854775807,508333333
'''
        result = profile.frames_from_dump(fixture)
        self.assertEqual(2, result['sampledFrames'])
        self.assertEqual(50, result['deadlineMissPercent'])
        self.assertEqual(9, result['frameTimeP95Ms'])

    def test_old_device_output_does_not_invent_deadline_or_fps(self):
        result = profile.frames_from_dump('Flags,IntendedVsync,FrameCompleted\n0,100000000,110000000\n')
        self.assertEqual(1, result['sampledFrames'])
        self.assertIsNone(result['deadlineMissPercent'])
        self.assertNotIn('fps', result)

    def test_no_frame_data_remains_unmeasured(self):
        result = profile.frames_from_dump('No process found')
        self.assertEqual(0, result['sampledFrames'])
        self.assertIsNone(result['frameTimeP95Ms'])

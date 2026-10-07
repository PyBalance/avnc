#!/usr/bin/env bash
set -euo pipefail

adb install -r apks/app-debug.apk
adb install -r apks/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.gaurav.avnc.session.ClipboardPasteTest,com.gaurav.avnc.ui.vnc.VirtualKeysTest,com.gaurav.avnc.ui.vnc.input.KeyHandlerTest,com.gaurav.avnc.vnc.VncClientTest \
  com.gaurav.avnc.debug.test/androidx.test.runner.AndroidJUnitRunner | tee instrumentation-results.txt

# am instrument can exit successfully even when tests fail.
python3 - <<'PY'
from pathlib import Path
import re
result = Path('instrumentation-results.txt').read_text()
assert re.search(r'OK \(\d+ tests?\)', result), result
assert 'FAILURES!!!' not in result and 'INSTRUMENTATION_FAILED' not in result, result
PY

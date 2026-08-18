from pathlib import Path
import re

path = Path('app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt')
text = path.read_text()

# The first v0.6 patch can leave HomeScreen closed immediately before the
# reimbursement dialog, with one or more blank lines in between. Remove that
# premature closing brace; the brace after the dialog then closes HomeScreen.
pattern = r'\n}\n(?:[ \t]*\n)+    if \(showReimbursement\) \{'
text, changed = re.subn(pattern, '\n\n    if (showReimbursement) {', text, count=1)
print('Moved reimbursement dialog inside HomeScreen' if changed else 'No premature HomeScreen closing brace found')

home_start = text.find('private fun HomeScreen(')
next_composable = text.find('@Composable\nprivate fun AdvanceSelectorRow', home_start)
dialog = text.find('if (showReimbursement)', home_start)
if home_start < 0 or next_composable < 0 or dialog < 0 or dialog > next_composable:
    raise SystemExit('Reimbursement dialog is not inside the HomeScreen section')

# A correctly placed dialog should be part of HomeScreen, so there must not be
# a top-level closing brace followed only by blank lines before the dialog.
segment = text[home_start:next_composable]
if re.search(r'\n}\n(?:[ \t]*\n)+    if \(showReimbursement\)', segment):
    raise SystemExit('HomeScreen still closes before reimbursement dialog')

path.write_text(text)

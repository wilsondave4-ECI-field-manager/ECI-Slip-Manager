from pathlib import Path

path = Path('app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt')
text = path.read_text()

# The first v0.6 patch can leave HomeScreen closed immediately before the
# reimbursement dialog. If so, remove that premature closing brace; the
# closing brace after the dialog then correctly closes HomeScreen.
bad = '\n}\n\n    if (showReimbursement) {'
if bad in text:
    text = text.replace(bad, '\n\n    if (showReimbursement) {', 1)
    print('Moved reimbursement dialog inside HomeScreen')
else:
    print('HomeScreen reimbursement placement already correct')

# Guard against accidentally leaving the dialog between top-level composables.
home_start = text.find('private fun HomeScreen(')
next_composable = text.find('@Composable\nprivate fun AdvanceSelectorRow', home_start)
dialog = text.find('if (showReimbursement)', home_start)
if home_start < 0 or next_composable < 0 or dialog < 0 or dialog > next_composable:
    raise SystemExit('Reimbursement dialog is not inside the HomeScreen section')

path.write_text(text)

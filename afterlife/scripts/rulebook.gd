extends RefCounted
## The day's rulebook. Rules are checked in priority order; the first that
## applies decides the verdict. Daily parameters (seal, contraband) re-roll
## every shift; the rest are fixed per run, except for the day-9 reclassification.

const Data = preload("res://scripts/data.gd")

var day := 1
var seal_idx := 0
var banned_item := ""
var furnace_cause := ""
var return_cause := ""
var archive_weekday := 0
var light_max := 30
var heavy_min := 60


func setup(p_day: int, run_seed: int) -> void:
	day = p_day

	var daily := RandomNumberGenerator.new()
	daily.seed = run_seed * 1000 + p_day
	seal_idx = daily.randi() % Data.SEALS.size()
	banned_item = Data.ITEMS[daily.randi() % Data.ITEMS.size()]

	var fixed := RandomNumberGenerator.new()
	fixed.seed = run_seed * 10 + (2 if p_day >= 9 else 1)
	var causes: Array = Data.CAUSES.duplicate()
	for i in range(causes.size() - 1, 0, -1):
		var j := fixed.randi() % (i + 1)
		var tmp = causes[i]
		causes[i] = causes[j]
		causes[j] = tmp
	furnace_cause = causes[0]
	return_cause = causes[1]
	archive_weekday = fixed.randi() % 7

	if p_day >= 9:
		light_max = 25
		heavy_min = 45
	elif p_day >= 7:
		light_max = 35
		heavy_min = 50
	else:
		light_max = 30
		heavy_min = 60


## Human-readable rules in priority order.
func rule_lines() -> Array:
	var lines: Array = []
	lines.append("Seal must be %s. Otherwise: [b]RETURN[/b]." % Data.SEALS[seal_idx].name)
	if day >= 2:
		lines.append("Name and age on the ledger must match the death slip. Otherwise: [b]RETURN[/b].")
	if day >= 3:
		lines.append("Carrying \"%s\" is contraband: [b]RETURN[/b]." % banned_item)
	if day >= 8:
		lines.append("Died of %s: not yet their time, [b]RETURN[/b]." % str(return_cause).to_lower())
	if day >= 5:
		lines.append("Under 13 years old: [b]MEADOW[/b], whatever the scales say.")
	if day >= 4:
		lines.append("Died of %s: [b]FURNACE[/b]." % str(furnace_cause).to_lower())
	if day >= 6:
		lines.append("Died on a %s: [b]ARCHIVE[/b]." % Data.WEEKDAYS[archive_weekday])
	lines.append("Otherwise by weight: under %d g [b]MEADOW[/b], %d to %d g [b]ARCHIVE[/b], %d g or more [b]FURNACE[/b]." % [light_max, light_max, heavy_min - 1, heavy_min])
	return lines


## BBCode rulebook. Lines not present in prev_lines are tagged NEW.
func bbcode(prev_lines: Array, only_new: bool = false) -> String:
	var out := ""
	var lines := rule_lines()
	for i in lines.size():
		var is_new := not prev_lines.has(lines[i])
		if only_new and not is_new:
			continue
		if is_new:
			out += "[color=#e8c35a][b]%d.[/b] %s [b](NEW)[/b][/color]\n\n" % [i + 1, lines[i]]
		else:
			out += "[b]%d.[/b] %s\n\n" % [i + 1, lines[i]]
	return out.strip_edges()


## Verdict for a soul: {decision:int, reason:String}
func evaluate(s: Dictionary) -> Dictionary:
	if s.seal != seal_idx:
		return _v(Data.RETURN, "The seal is %s; today's seal is %s." % [Data.SEALS[s.seal].name, Data.SEALS[seal_idx].name])
	if day >= 2:
		if s.ledger_name != s.slip_name:
			return _v(Data.RETURN, "The name differs: ledger says \"%s\", slip says \"%s\"." % [s.ledger_name, s.slip_name])
		if s.ledger_age != s.slip_age:
			return _v(Data.RETURN, "The age differs: ledger says %d, slip says %d." % [s.ledger_age, s.slip_age])
	if day >= 3 and s.item == banned_item:
		return _v(Data.RETURN, "\"%s\" is contraband today." % banned_item)
	if day >= 8 and s.cause == return_cause:
		return _v(Data.RETURN, "Died of %s: not yet their time." % str(return_cause).to_lower())
	if day >= 5 and s.ledger_age < 13:
		return _v(Data.MEADOW, "Under 13: always the Meadow.")
	if day >= 4 and s.cause == furnace_cause:
		return _v(Data.FURNACE, "Died of %s: the Furnace." % str(furnace_cause).to_lower())
	if day >= 6 and s.weekday == archive_weekday:
		return _v(Data.ARCHIVE, "Died on a %s: the Archive." % Data.WEEKDAYS[archive_weekday])
	if s.weight < light_max:
		return _v(Data.MEADOW, "%d g is under %d g: the Meadow." % [s.weight, light_max])
	if s.weight >= heavy_min:
		return _v(Data.FURNACE, "%d g is %d g or more: the Furnace." % [s.weight, heavy_min])
	return _v(Data.ARCHIVE, "%d g is in the middle band: the Archive." % s.weight)


## Field keys the Lens should highlight.
func lens_keys(s: Dictionary) -> Array:
	var keys: Array = []
	if s.seal != seal_idx:
		keys.append("l_seal")
	if day >= 2:
		if s.ledger_name != s.slip_name:
			keys.append_array(["l_name", "s_name"])
		if s.ledger_age != s.slip_age:
			keys.append_array(["l_age", "s_age"])
	return keys


func _v(decision: int, reason: String) -> Dictionary:
	return {"decision": decision, "reason": reason}

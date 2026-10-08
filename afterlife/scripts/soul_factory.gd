extends RefCounted
## Generates arriving souls consistent with the day's rulebook.

const Data = preload("res://scripts/data.gd")

var rng := RandomNumberGenerator.new()


func shift_size(day: int) -> int:
	return 8 + mini(day, 6)


## Build the queue for a shift. Story souls are slotted in after the first soul.
func make_queue(rb, day: int) -> Array:
	var n := shift_size(day)
	var queue: Array = []
	for i in n:
		queue.append(make(rb))
	for entry in Data.STORY:
		if entry.day == day:
			queue[rng.randi_range(1, n - 1)] = make(rb, entry)
	return queue


func make(rb, story: Dictionary = {}) -> Dictionary:
	var s := {}
	for attempt in 80:
		s = _roll(rb, story)
		var verdict: Dictionary = rb.evaluate(s)
		s["decision"] = verdict.decision
		s["reason"] = verdict.reason
		# A mercy plea only matters if the rules say something other than Meadow.
		if story.is_empty() or verdict.decision != Data.MEADOW:
			break
	return s


func _roll(rb, story: Dictionary) -> Dictionary:
	var s := {}
	var nm: String = story.get("name", "%s %s" % [_pick(Data.FIRST_NAMES), _pick(Data.LAST_NAMES)])
	var age: int
	if story.has("age"):
		age = story.age
	elif rng.randf() < 0.14:
		age = rng.randi_range(4, 12)
	else:
		age = rng.randi_range(13, 96)

	s["ledger_name"] = nm
	s["slip_name"] = nm
	s["ledger_age"] = age
	s["slip_age"] = age
	s["seal"] = rb.seal_idx
	s["cause"] = _pick(Data.CAUSES)
	s["weekday"] = rng.randi() % 7
	s["weight"] = rng.randi_range(5, 55) if age < 13 else rng.randi_range(5, 90)
	s["item"] = rb.banned_item if (rb.day >= 3 and rng.randf() < 0.09) else _pick(Data.ITEMS)

	var p_defect := 0.15 + 0.01 * mini(rb.day, 10)
	if not story.is_empty():
		p_defect = 0.5
	if rng.randf() < p_defect:
		var kinds: Array = ["seal"]
		if rb.day >= 2:
			kinds.append_array(["name", "age"])
		match kinds[rng.randi() % kinds.size()]:
			"seal":
				s["seal"] = (rb.seal_idx + rng.randi_range(1, Data.SEALS.size() - 1)) % Data.SEALS.size()
			"name":
				s["slip_name"] = _typo(nm)
			"age":
				var delta := rng.randi_range(1, 9)
				s["slip_age"] = maxi(1, age + (delta if rng.randf() < 0.5 else -delta))
				if s["slip_age"] == age:
					s["slip_age"] = age + 1

	s["hue"] = rng.randf()
	s["mood"] = rng.randi() % 4
	s["line"] = story.get("line", _pick(Data.GENERIC_LINES))
	s["story"] = not story.is_empty()
	return s


func _pick(arr: Array):
	return arr[rng.randi() % arr.size()]


## One-letter misspelling, e.g. "Crook" -> "Crock".
func _typo(nm: String) -> String:
	var letters := "aeiourlnstmd"
	for attempt in 20:
		var i := rng.randi() % nm.length()
		var ch := nm[i]
		if ch == " ":
			continue
		var repl := letters[rng.randi() % letters.length()]
		if i == 0 or nm[i - 1] == " ":
			repl = repl.to_upper()
		if repl != ch:
			return nm.substr(0, i) + repl + nm.substr(i + 1)
	return nm + "e"

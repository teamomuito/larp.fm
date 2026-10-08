extends SceneTree
## Logic test: godot --headless --path afterlife --script tests/sim.gd

const Data = preload("res://scripts/data.gd")
const Rulebook = preload("res://scripts/rulebook.gd")
const SoulFactory = preload("res://scripts/soul_factory.gd")

var failures := 0


func check(cond: bool, msg: String) -> void:
	if not cond:
		failures += 1
		printerr("FAIL: " + msg)


func _init() -> void:
	var dist := {}
	var mercy_pleas := 0
	var defect_souls := 0
	var lens_ok := true
	for run_seed in [1, 42, 777, 123456]:
		for day in range(1, 15):
			var rb = Rulebook.new()
			rb.setup(day, run_seed)
			check(rb.furnace_cause != rb.return_cause, "furnace/return cause collide")
			check(rb.rule_lines().size() >= 2, "rule lines")
			check(rb.bbcode([]).length() > 0, "bbcode")
			var f = SoulFactory.new()
			f.rng.seed = run_seed + day
			var q: Array = f.make_queue(rb, day)
			check(q.size() == f.shift_size(day), "queue size day %d" % day)
			for s in q:
				check(s.decision >= 0 and s.decision <= 3, "decision range")
				check(s.reason.length() > 0, "reason")
				var key := "d%d_%d" % [day, s.decision]
				dist[key] = dist.get(key, 0) + 1
				var lens: Array = rb.lens_keys(s)
				if not lens.is_empty():
					defect_souls += 1
					check(s.decision == Data.RETURN, "lens defect must mean RETURN (day %d)" % day)
				if s.story:
					mercy_pleas += 1
					check(s.decision != Data.MEADOW, "story soul must conflict with Meadow (day %d, %s)" % [day, s.ledger_name])
				if day == 1:
					check(s.ledger_name == s.slip_name and s.ledger_age == s.slip_age, "day1 has no name/age defects")
	# Rules are stable per (run_seed, day)
	var a = Rulebook.new()
	var b = Rulebook.new()
	a.setup(5, 99)
	b.setup(5, 99)
	check(a.rule_lines() == b.rule_lines(), "rulebook deterministic")
	# New-rule diff
	var d4 = Rulebook.new()
	d4.setup(4, 99)
	var d5 = Rulebook.new()
	d5.setup(5, 99)
	check(d5.bbcode(d4.rule_lines(), true).contains("Under 13"), "day 5 announces child rule")

	print("souls with lens defects: %d, story souls: %d" % [defect_souls, mercy_pleas])
	var keys := dist.keys()
	keys.sort()
	print(dist)
	if failures == 0:
		print("SIM OK")
	quit(1 if failures > 0 else 0)

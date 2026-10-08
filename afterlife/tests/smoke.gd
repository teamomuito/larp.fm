extends SceneTree
## UI smoke test: plays full games headlessly. godot --headless --path afterlife --script tests/smoke.gd

var failures := 0


func check(cond: bool, msg: String) -> void:
	if not cond:
		failures += 1
		printerr("FAIL: " + msg)


func _init() -> void:
	var main = load("res://scenes/main.tscn").instantiate()
	main.instant = true
	root.add_child(main)
	await process_frame

	# 1. Perfect play wins.
	main._on_new_game()
	await process_frame
	var shifts := 0
	var guard := 0
	while guard < 400 and main.debt > 0:
		guard += 1
		if main.card_screen.visible:
			# Intro card or shift summary: continue via the last (primary) button.
			var btn: Button = main.card_box.get_child(main.card_box.get_child_count() - 1)
			btn.pressed.emit()
			await process_frame
			continue
		main._decide(main.cur.decision)
		await process_frame
	check(main.debt <= 0, "perfect play clears debt (debt=%d, day=%d)" % [main.debt, main.day])
	check(main.total_wrong == 0, "no citations on perfect play")
	print("perfect play: debt %d after day %d, %d correct" % [main.debt, main.day, main.total_correct])
	# Finale and ending
	var btn: Button = main.card_box.get_child(main.card_box.get_child_count() - 1)
	btn.pressed.emit()  # open last file
	await process_frame
	for choice in ["meadow", "archive", "furnace", "stay"]:
		main._ending(choice)
		await process_frame

	# 2. Always-wrong play ends in game over.
	main._on_new_game()
	await process_frame
	main._begin_shift()
	guard = 0
	while guard < 400 and main.debt < main.LOSE_DEBT:
		guard += 1
		if main.card_screen.visible:
			var b2: Button = main.card_box.get_child(main.card_box.get_child_count() - 1)
			b2.pressed.emit()
			await process_frame
			continue
		if main.fb_panel.visible:
			main._on_feedback_next()
			await process_frame
			continue
		main._decide((main.cur.decision + 1) % 4)
		await process_frame
	if main.fb_panel.visible:
		main._on_feedback_next()
	check(main.debt >= main.LOSE_DEBT, "wrong play loses")
	check(main.card_screen.visible, "game over card shown")

	# 3. Lens, rulebook modal, save/continue.
	main._on_new_game()
	await process_frame
	main._begin_shift()
	main._on_lens()
	main._open_rulebook()
	check(main.modal.visible, "rulebook modal opens")
	main._save()
	check(main._load(), "save loads")

	print("SMOKE OK" if failures == 0 else "SMOKE FAILED")
	quit(1 if failures > 0 else 0)

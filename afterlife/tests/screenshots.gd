extends SceneTree
## Renders key screens to PNGs (needs a display, e.g. xvfb-run):
## xvfb-run -a godot --path afterlife --script tests/screenshots.gd -- /output/dir

func _init() -> void:
	var out := "/tmp"
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out = args[0]
	root.size = Vector2i(720, 1280)
	var main = load("res://scenes/main.tscn").instantiate()
	main.instant = true
	root.add_child(main)
	await _shot(out, "01_title")
	main._on_new_game()
	await _shot(out, "02_intro")
	main.day = 5
	main.run_seed = 7
	main._start_intro()
	await _shot(out, "03_intro_day5")
	main._begin_shift()
	main.queue[0]["ledger_name"] = "Bram Crook"
	await _shot(out, "04_shift")
	main._on_lens()
	main.fields["l_weight"].button_pressed = true
	await _shot(out, "05_lens")
	main._decide((main.cur.decision + 1) % 4)
	await _shot(out, "06_feedback")
	main._open_rulebook()
	await _shot(out, "07_rulebook")
	main.modal.visible = false
	main._finale()
	await _shot(out, "08_finale")
	main._ending("meadow")
	await _shot(out, "09_ending")
	quit()


func _shot(dir: String, name: String) -> void:
	for i in 4:
		await process_frame
	var img := root.get_texture().get_image()
	img.save_png("%s/%s.png" % [dir, name])

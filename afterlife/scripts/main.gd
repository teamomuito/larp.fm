extends Control
## Afterlife Clerk: game flow and UI. All UI is built in code.

const Data = preload("res://scripts/data.gd")
const Rulebook = preload("res://scripts/rulebook.gd")
const SoulFactory = preload("res://scripts/soul_factory.gd")
const GhostView = preload("res://scripts/ghost_view.gd")
const Sfx = preload("res://scripts/sfx.gd")

const SAVE_PATH := "user://afterlife.cfg"
const START_DEBT := 280
const LOSE_DEBT := 350
const CREDIT := 3
const PENALTY := 7
const INTEREST := 6
const AUDIT_FINE := 15
const LENS_CHARGES := 3

const C_BG := Color(0.08, 0.07, 0.12)
const C_PANEL := Color(0.15, 0.13, 0.24)
const C_PANEL2 := Color(0.22, 0.2, 0.34)
const C_TEXT := Color(0.92, 0.9, 0.98)
const C_DIM := Color(0.62, 0.6, 0.74)
const C_GOLD := Color(0.91, 0.76, 0.35)
const C_GOOD := Color(0.5, 0.85, 0.62)
const C_BAD := Color(0.95, 0.45, 0.4)
const C_PAPER := Color(0.94, 0.89, 0.76)
const C_INK := Color(0.16, 0.12, 0.1)
const C_INK_DIM := Color(0.45, 0.38, 0.3)
const REALM_COLORS := [
	Color(0.2, 0.55, 0.38),
	Color(0.25, 0.4, 0.72),
	Color(0.76, 0.32, 0.16),
	Color(0.4, 0.4, 0.48),
]

## Skip delays and randomness-driven pauses (used by the smoke test).
var instant := false

# Run state
var run_seed := 0
var day := 1
var debt := START_DEBT
var mercy := 0
var total_correct := 0
var total_wrong := 0

# Shift state
var rb
var prev_lines: Array = []
var factory := SoulFactory.new()
var rng := RandomNumberGenerator.new()
var queue: Array = []
var cur: Dictionary = {}
var locked := true
var lens_charges := 0
var day_correct := 0
var day_wrong := 0
var day_mercy := 0

# UI
var sfx
var title_screen: Control
var shift_screen: Control
var card_screen: Control
var card_box: VBoxContainer
var modal: Control
var modal_title: Label
var modal_text: RichTextLabel
var continue_btn: Button

var lbl_shift: Label
var lbl_debt: Label
var lbl_left: Label
var lbl_seal: Label
var seal_chip: ColorRect
var ghost
var lbl_speech: Label
var papers: Control
var fields := {}
var swatches := {}
var lens_btn: Button
var decision_btns: Array = []
var toast: Label
var fb_panel: PanelContainer
var fb_label: RichTextLabel
var fb_next: Button


func _ready() -> void:
	rng.randomize()
	factory.rng = rng
	sfx = Sfx.new()
	add_child(sfx)

	var bg := ColorRect.new()
	bg.color = C_BG
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)

	_build_title()
	_build_shift()
	_build_card()
	_build_modal()
	_show(title_screen)


# ---------------------------------------------------------------- UI helpers

func _style(bg: Color, radius := 18, border := Color(0, 0, 0, 0), border_w := 0, pad := 14) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.set_corner_radius_all(radius)
	s.border_color = border
	s.set_border_width_all(border_w)
	s.content_margin_left = pad
	s.content_margin_right = pad
	s.content_margin_top = pad * 0.6
	s.content_margin_bottom = pad * 0.6
	return s


func _label(text: String, size := 26, color := C_TEXT, align := HORIZONTAL_ALIGNMENT_LEFT, wrap := false) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	l.horizontal_alignment = align
	if wrap:
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return l


func _rich(size := 28, color := C_TEXT) -> RichTextLabel:
	var r := RichTextLabel.new()
	r.bbcode_enabled = true
	r.fit_content = true
	r.scroll_active = false
	r.add_theme_font_size_override("normal_font_size", size)
	r.add_theme_font_size_override("bold_font_size", size)
	r.add_theme_font_size_override("italics_font_size", size)
	r.add_theme_color_override("default_color", color)
	r.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return r


func _button(text: String, bg: Color, cb: Callable, font := 30, min_h := 90) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0, min_h)
	b.add_theme_font_size_override("font_size", font)
	b.add_theme_color_override("font_color", C_TEXT)
	b.add_theme_color_override("font_hover_color", C_TEXT)
	b.add_theme_color_override("font_pressed_color", C_TEXT)
	b.add_theme_color_override("font_disabled_color", C_DIM)
	b.add_theme_stylebox_override("normal", _style(bg))
	b.add_theme_stylebox_override("hover", _style(bg.lightened(0.08)))
	b.add_theme_stylebox_override("pressed", _style(bg.darkened(0.25)))
	b.add_theme_stylebox_override("disabled", _style(bg.darkened(0.5)))
	b.add_theme_stylebox_override("focus", StyleBoxEmpty.new())
	b.pressed.connect(cb)
	return b


func _full_margin(parent: Control, h: int, v: int) -> MarginContainer:
	var m := MarginContainer.new()
	m.set_anchors_preset(Control.PRESET_FULL_RECT)
	m.add_theme_constant_override("margin_left", h)
	m.add_theme_constant_override("margin_right", h)
	m.add_theme_constant_override("margin_top", v)
	m.add_theme_constant_override("margin_bottom", v)
	parent.add_child(m)
	return m


func _screen() -> Control:
	var c := Control.new()
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(c)
	return c


func _show(screen: Control) -> void:
	for s in [title_screen, shift_screen, card_screen]:
		s.visible = (s == screen)


# --------------------------------------------------------------------- title

func _build_title() -> void:
	title_screen = _screen()
	var m := _full_margin(title_screen, 40, 60)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 16)
	m.add_child(v)

	v.add_child(_label("AFTERLIFE\nCLERK", 84, C_GOLD, HORIZONTAL_ALIGNMENT_CENTER))
	v.add_child(_label("Lighthouse Customs for the Dead", 28, C_DIM, HORIZONTAL_ALIGNMENT_CENTER))

	var g = GhostView.new()
	g.hue = 0.6
	g.mood = 3
	g.size_flags_vertical = Control.SIZE_EXPAND_FILL
	g.custom_minimum_size = Vector2(0, 280)
	v.add_child(g)

	continue_btn = _button("Continue", REALM_COLORS[0], _on_continue)
	v.add_child(continue_btn)
	v.add_child(_button("New Game", C_PANEL2, _on_new_game))
	v.add_child(_button("How to Play", C_PANEL, _open_howto, 28, 76))
	continue_btn.visible = FileAccess.file_exists(SAVE_PATH)


# --------------------------------------------------------------------- shift

func _build_shift() -> void:
	shift_screen = _screen()
	var m := _full_margin(shift_screen, 20, 16)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 10)
	m.add_child(v)

	# Top bar
	var top := HBoxContainer.new()
	lbl_shift = _label("", 28, C_TEXT)
	lbl_shift.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lbl_debt = _label("", 28, C_GOLD, HORIZONTAL_ALIGNMENT_CENTER)
	lbl_debt.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lbl_left = _label("", 28, C_DIM, HORIZONTAL_ALIGNMENT_RIGHT)
	lbl_left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(lbl_shift)
	top.add_child(lbl_debt)
	top.add_child(lbl_left)
	var rules_btn := _button("Rules", C_PANEL2, _open_rulebook, 24, 52)
	rules_btn.custom_minimum_size.x = 120
	top.add_child(rules_btn)
	v.add_child(top)

	# Seal reminder
	var seal_row := HBoxContainer.new()
	seal_row.alignment = BoxContainer.ALIGNMENT_CENTER
	seal_row.add_theme_constant_override("separation", 10)
	seal_row.add_child(_label("Today's seal:", 26, C_DIM))
	seal_chip = ColorRect.new()
	seal_chip.custom_minimum_size = Vector2(30, 30)
	seal_chip.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	seal_row.add_child(seal_chip)
	lbl_seal = _label("", 26, C_TEXT)
	seal_row.add_child(lbl_seal)
	v.add_child(seal_row)

	# Stage: ghost + speech
	ghost = GhostView.new()
	ghost.size_flags_vertical = Control.SIZE_EXPAND_FILL
	ghost.custom_minimum_size = Vector2(0, 200)
	v.add_child(ghost)
	var speech_panel := PanelContainer.new()
	speech_panel.add_theme_stylebox_override("panel", _style(C_PANEL, 16, C_PANEL2, 2))
	speech_panel.custom_minimum_size = Vector2(0, 104)
	lbl_speech = _label("", 25, C_TEXT, HORIZONTAL_ALIGNMENT_CENTER, true)
	lbl_speech.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	speech_panel.add_child(lbl_speech)
	v.add_child(speech_panel)

	# Papers
	papers = HBoxContainer.new()
	papers.add_theme_constant_override("separation", 12)
	var ledger := _paper("LEDGER")
	var slip := _paper("DEATH SLIP")
	_add_field(ledger, "l_name", "Name")
	_add_field(ledger, "l_age", "Age")
	_add_field(ledger, "l_weight", "Soul weight")
	_add_field(ledger, "l_seal", "Seal", true)
	_add_field(ledger, "l_item", "Carried")
	_add_field(slip, "s_name", "Name")
	_add_field(slip, "s_age", "Age")
	_add_field(slip, "s_cause", "Died of")
	_add_field(slip, "s_day", "Died on")
	papers.add_child(ledger.get_parent())
	papers.add_child(slip.get_parent())
	v.add_child(papers)

	# Lens row
	lens_btn = _button("", C_PANEL2, _on_lens, 26, 60)
	v.add_child(lens_btn)

	# Decisions
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 10)
	grid.add_theme_constant_override("v_separation", 10)
	var captions := ["MEADOW\nrest", "ARCHIVE\nremember", "FURNACE\nburn", "RETURN\nsend back"]
	for i in 4:
		var b := _button(captions[i], REALM_COLORS[i], _decide.bind(i), 30, 112)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_child(b)
		decision_btns.append(b)
	v.add_child(grid)

	# Toast
	toast = _label("", 34, C_GOLD, HORIZONTAL_ALIGNMENT_CENTER)
	toast.set_anchors_preset(Control.PRESET_CENTER_TOP)
	toast.offset_left = -300
	toast.offset_right = 300
	toast.offset_top = 330
	toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	toast.modulate.a = 0.0
	shift_screen.add_child(toast)

	# Feedback bottom sheet
	fb_panel = PanelContainer.new()
	fb_panel.add_theme_stylebox_override("panel", _style(C_PANEL, 22, C_GOLD, 3, 20))
	fb_panel.anchor_left = 0.0
	fb_panel.anchor_right = 1.0
	fb_panel.anchor_top = 1.0
	fb_panel.anchor_bottom = 1.0
	fb_panel.offset_left = 20
	fb_panel.offset_right = -20
	fb_panel.offset_top = -380
	fb_panel.offset_bottom = -16
	var fv := VBoxContainer.new()
	fv.add_theme_constant_override("separation", 12)
	fb_label = _rich(27)
	fb_label.size_flags_vertical = Control.SIZE_EXPAND_FILL
	fv.add_child(fb_label)
	fb_next = _button("Next soul", C_PANEL2, _on_feedback_next, 30, 80)
	fv.add_child(fb_next)
	fb_panel.add_child(fv)
	fb_panel.visible = false
	shift_screen.add_child(fb_panel)


func _paper(title: String) -> VBoxContainer:
	var panel := PanelContainer.new()
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.size_flags_stretch_ratio = 1.0
	panel.add_theme_stylebox_override("panel", _style(C_PAPER, 10, C_INK_DIM, 2, 14))
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 2)
	v.add_child(_label(title, 22, C_INK_DIM, HORIZONTAL_ALIGNMENT_CENTER))
	panel.add_child(v)
	return v


func _add_field(parent: VBoxContainer, key: String, caption: String, with_swatch := false) -> void:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 0)
	box.add_child(_label(caption.to_upper(), 16, C_INK_DIM))
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 8)
	if with_swatch:
		var sw := ColorRect.new()
		sw.custom_minimum_size = Vector2(24, 24)
		sw.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		row.add_child(sw)
		swatches[key] = sw
	var b := Button.new()
	b.toggle_mode = true
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b.clip_text = true
	b.add_theme_font_size_override("font_size", 25)
	for c in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_hover_pressed_color"]:
		b.add_theme_color_override(c, C_INK)
	_style_field(b, false)
	row.add_child(b)
	box.add_child(row)
	parent.add_child(box)
	fields[key] = b


func _style_field(b: Button, lens: bool) -> void:
	var base := StyleBoxFlat.new()
	base.set_corner_radius_all(14)
	base.content_margin_left = 6
	base.content_margin_right = 6
	base.content_margin_top = 1
	base.content_margin_bottom = 1
	if lens:
		base.bg_color = Color(1.0, 0.85, 0.2, 0.55)
		base.border_color = Color(0.85, 0.6, 0.0)
		base.set_border_width_all(2)
	else:
		base.bg_color = Color(0, 0, 0, 0)
	var mark := base.duplicate() as StyleBoxFlat
	mark.border_color = Color(0.8, 0.15, 0.15)
	mark.set_border_width_all(3)
	b.add_theme_stylebox_override("normal", base)
	b.add_theme_stylebox_override("hover", base)
	b.add_theme_stylebox_override("focus", base)
	b.add_theme_stylebox_override("pressed", mark)
	b.add_theme_stylebox_override("hover_pressed", mark)


# ---------------------------------------------------------------------- card

func _build_card() -> void:
	card_screen = _screen()
	var m := _full_margin(card_screen, 36, 56)
	card_box = VBoxContainer.new()
	card_box.add_theme_constant_override("separation", 20)
	m.add_child(card_box)


## Generic full-screen card: title, rich body, then buttons [[text, color, callable], ...].
func _card(title: String, body: String, buttons: Array, accent := C_GOLD) -> void:
	for c in card_box.get_children():
		card_box.remove_child(c)
		c.queue_free()
	card_box.add_child(_label(title, 50, accent, HORIZONTAL_ALIGNMENT_CENTER))
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var rt := _rich(29)
	rt.text = body
	scroll.add_child(rt)
	card_box.add_child(scroll)
	for b in buttons:
		card_box.add_child(_button(b[0], b[1], b[2]))
	_show(card_screen)


# --------------------------------------------------------------------- modal

func _build_modal() -> void:
	modal = _screen()
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.75)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	modal.add_child(dim)
	var m := _full_margin(modal, 28, 60)
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", _style(C_PANEL, 24, C_GOLD, 3, 24))
	m.add_child(panel)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 16)
	panel.add_child(v)
	modal_title = _label("", 42, C_GOLD, HORIZONTAL_ALIGNMENT_CENTER)
	v.add_child(modal_title)
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	modal_text = _rich(28)
	scroll.add_child(modal_text)
	v.add_child(scroll)
	v.add_child(_button("Close", C_PANEL2, func(): modal.visible = false, 30, 80))
	modal.visible = false


func _open_modal(title: String, body: String) -> void:
	modal_title.text = title
	modal_text.text = body
	modal.visible = true


func _open_howto() -> void:
	_open_modal("How to Play", Data.HOW_TO)


func _open_rulebook() -> void:
	if rb == null:
		return
	_open_modal("Rulebook: Shift %d" % day, rb.bbcode(prev_lines) + "\n\n[color=#9c9ab8]The first rule that applies decides the verdict.[/color]")


# ---------------------------------------------------------------- game flow

func _on_new_game() -> void:
	run_seed = rng.randi_range(1, 999999)
	day = 1
	debt = START_DEBT
	mercy = 0
	total_correct = 0
	total_wrong = 0
	_start_intro()


func _on_continue() -> void:
	if not _load():
		_on_new_game()
		return
	_start_intro()


func _start_intro() -> void:
	rb = Rulebook.new()
	rb.setup(day, run_seed)
	prev_lines = []
	if day > 1:
		var prev := Rulebook.new()
		prev.setup(day - 1, run_seed)
		prev_lines = prev.rule_lines()
	_save()
	var body := Data.day_intro(day)
	body += "\n\n[b]Rulebook changes today[/b]\n\n" + rb.bbcode(prev_lines, true)
	body += "\n\n[color=#9c9ab8]Debt: %d   |   Souls waiting: %d[/color]" % [debt, factory.shift_size(day)]
	_card("Shift %d" % day, body, [
		["Open Rulebook", C_PANEL2, _open_rulebook],
		["Begin Shift", REALM_COLORS[0], _begin_shift],
	])


func _begin_shift() -> void:
	queue = factory.make_queue(rb, day)
	lens_charges = LENS_CHARGES
	day_correct = 0
	day_wrong = 0
	day_mercy = 0
	var sd: Dictionary = Data.SEALS[rb.seal_idx]
	seal_chip.color = sd.color
	lbl_seal.text = sd.name
	_show(shift_screen)
	_next_soul()


func _next_soul() -> void:
	fb_panel.visible = false
	cur = queue.pop_front()
	for k in fields:
		var b: Button = fields[k]
		b.button_pressed = false
		_style_field(b, false)
	fields["l_name"].text = cur.ledger_name
	fields["l_age"].text = str(cur.ledger_age)
	fields["l_weight"].text = "%d g" % cur.weight
	fields["l_seal"].text = Data.SEALS[cur.seal].name
	swatches["l_seal"].color = Data.SEALS[cur.seal].color
	fields["l_item"].text = cur.item
	fields["s_name"].text = cur.slip_name
	fields["s_age"].text = str(cur.slip_age)
	fields["s_cause"].text = cur.cause
	fields["s_day"].text = Data.WEEKDAYS[cur.weekday]

	ghost.hue = cur.hue
	ghost.mood = cur.mood
	ghost.size_scale = 0.78 if cur.ledger_age < 13 else 1.0
	lbl_speech.text = "\"%s\"" % cur.line
	_update_hud()

	if not instant:
		ghost.modulate.a = 0.0
		papers.modulate.a = 0.0
		var tw := create_tween().set_parallel(true)
		tw.tween_property(ghost, "modulate:a", 1.0, 0.3)
		tw.tween_property(papers, "modulate:a", 1.0, 0.3)
	locked = false


func _update_hud() -> void:
	lbl_shift.text = "Shift %d" % day
	lbl_debt.text = "Debt %d" % debt
	lbl_left.text = "Left %d" % (queue.size() + 1)
	lens_btn.text = "Lens   (%d left)" % lens_charges
	lens_btn.disabled = lens_charges <= 0


func _on_lens() -> void:
	if locked or lens_charges <= 0:
		return
	lens_charges -= 1
	var keys: Array = rb.lens_keys(cur)
	for k in keys:
		_style_field(fields[k], true)
	if keys.is_empty():
		_toast("The lens finds nothing amiss.", C_DIM)
	else:
		_toast("Something is wrong here...", C_GOLD)
	_update_hud()


func _decide(choice: int) -> void:
	if locked:
		return
	locked = true
	sfx.play("stamp")
	var correct: int = cur.decision
	if choice == correct:
		day_correct += 1
		total_correct += 1
		debt -= CREDIT
		sfx.play("good")
		_toast("Stamped.  Debt -%d" % CREDIT, C_GOOD)
		_update_hud()
		if not instant:
			await get_tree().create_timer(0.55).timeout
		_advance()
	elif cur.story and choice == Data.MEADOW:
		mercy += 1
		day_mercy += 1
		_update_hud()
		_feedback("[b][color=#e8c35a]Mercy[/color][/b]\n\n%s walks into the Meadow.\n\nThe rulebook said [b]%s[/b]: %s\nNo citation, no credit. The Auditor reads the stamps." % [
			cur.ledger_name, Data.REALMS[correct].to_upper(), cur.reason])
	else:
		day_wrong += 1
		total_wrong += 1
		debt += PENALTY
		sfx.play("bad")
		if not instant:
			Input.vibrate_handheld(60)
		_update_hud()
		_feedback("[b][color=#f27a6a]Citation!  Debt +%d[/color][/b]\n\nYou stamped [b]%s[/b].\nThe rules said [b]%s[/b].\n\n%s" % [
			PENALTY, Data.REALMS[choice].to_upper(), Data.REALMS[correct].to_upper(), cur.reason])


func _feedback(text: String) -> void:
	fb_label.text = text
	fb_panel.visible = true


func _on_feedback_next() -> void:
	fb_panel.visible = false
	_advance()


func _advance() -> void:
	if debt >= LOSE_DEBT:
		_game_over()
	elif queue.is_empty():
		_end_shift()
	else:
		_next_soul()


func _toast(text: String, color: Color) -> void:
	toast.text = text
	toast.add_theme_color_override("font_color", color)
	if instant:
		return
	toast.modulate.a = 1.0
	var tw := create_tween()
	tw.tween_interval(0.5)
	tw.tween_property(toast, "modulate:a", 0.0, 0.35)


func _end_shift() -> void:
	debt += INTEREST
	var body := "[b]Souls stamped correctly:[/b] %d\n[b]Citations:[/b] %d\n[b]Mercies:[/b] %d\n\nInterest charged: +%d" % [day_correct, day_wrong, day_mercy, INTEREST]
	if day_mercy > 0 and rng.randf() < minf(0.35 * day_mercy, 0.9):
		debt += AUDIT_FINE
		body += "\n[color=#f27a6a]The Auditor found your mercy stamps and fined you %d.[/color]" % AUDIT_FINE
	body += "\n\n[b]Debt: %d[/b]\n\n[i]%s[/i]" % [maxi(debt, 0), Data.day_end(day)]

	if debt >= LOSE_DEBT:
		_game_over()
	elif debt <= 0:
		_card("Shift %d done" % day, body + "\n\n[color=#e8c35a]Your debt is paid. Something is waiting in the tray.[/color]", [
			["Open the last file", REALM_COLORS[0], _finale],
		])
	else:
		day += 1
		_save()
		_card("Shift %d done" % (day - 1), body, [
			["Next shift", REALM_COLORS[0], _start_intro],
		])


func _game_over() -> void:
	_delete_save()
	_card("Reassigned", "The debt grew past what the Lighthouse could forgive. The Warden hands you a shovel and a pair of heavy gloves.\n\n\"The Furnace needs stokers,\" they say. \"You'll be wonderful.\"\n\n[color=#9c9ab8]Shifts survived: %d\nCorrect stamps: %d\nCitations: %d[/color]" % [day, total_correct, total_wrong], [
		["Try again", C_PANEL2, _on_new_game],
		["Title", C_PANEL, _to_title],
	], C_BAD)


func _finale() -> void:
	_delete_save()
	var body := "The tray in the corner holds one file. The name is yours. Cause of death: [i]overwork[/i]. Weight: exactly as heavy as it ought to be.\n\nThe Warden stands behind you, holding their hat. \"You've cleared the debt,\" they say. \"I can't stamp this one. It has to be you.\""
	_card("Your File", body, [
		["Meadow: rest", REALM_COLORS[0], _ending.bind("meadow")],
		["Archive: be remembered", REALM_COLORS[1], _ending.bind("archive")],
		["Furnace: keep the lamp lit", REALM_COLORS[2], _ending.bind("furnace")],
		["Take the Warden's stamp", C_PANEL2, _ending.bind("stay")],
	])


func _ending(choice: String) -> void:
	var e: Dictionary = Data.ENDINGS[choice]
	var kind := mercy >= 3
	var body: String = e.kind if kind else e.strict
	body += "\n\n[color=#9c9ab8]Correct stamps: %d\nCitations: %d\nMercies: %d\n%s[/color]" % [
		total_correct, total_wrong, mercy,
		"You were known for your mercy." if kind else "You were known for your discipline."]
	_card(e.title, body, [
		["Back to title", C_PANEL2, _to_title],
	])


func _to_title() -> void:
	continue_btn.visible = FileAccess.file_exists(SAVE_PATH)
	_show(title_screen)


# ------------------------------------------------------------------- saving

func _save() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("run", "seed", run_seed)
	cfg.set_value("run", "day", day)
	cfg.set_value("run", "debt", debt)
	cfg.set_value("run", "mercy", mercy)
	cfg.set_value("run", "correct", total_correct)
	cfg.set_value("run", "wrong", total_wrong)
	cfg.save(SAVE_PATH)


func _load() -> bool:
	var cfg := ConfigFile.new()
	if cfg.load(SAVE_PATH) != OK:
		return false
	run_seed = cfg.get_value("run", "seed", 0)
	day = cfg.get_value("run", "day", 1)
	debt = cfg.get_value("run", "debt", START_DEBT)
	mercy = cfg.get_value("run", "mercy", 0)
	total_correct = cfg.get_value("run", "correct", 0)
	total_wrong = cfg.get_value("run", "wrong", 0)
	return run_seed != 0


func _delete_save() -> void:
	if FileAccess.file_exists(SAVE_PATH):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(SAVE_PATH))

extends Control
## Procedurally drawn ghost. Hue, size and mood vary per soul.

var hue := 0.6
var size_scale := 1.0
var mood := 0
var _t := 0.0


func _process(delta: float) -> void:
	_t += delta
	queue_redraw()


func _draw() -> void:
	var r := minf(size.x, size.y) * 0.3 * size_scale
	var c := Vector2(size.x * 0.5, size.y * 0.45 + sin(_t * 2.0) * 7.0)
	var body := Color.from_hsv(hue, 0.22, 0.97, 0.92)
	var ink := Color(0.1, 0.09, 0.16)

	# Ground shadow
	draw_set_transform(Vector2(size.x * 0.5, size.y * 0.9), 0.0, Vector2(1.0, 0.22))
	draw_circle(Vector2.ZERO, r * 0.85, Color(0, 0, 0, 0.28))
	draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)

	# Body: dome on top, wavy hem below
	var pts := PackedVector2Array()
	var steps := 24
	for i in steps + 1:
		var a := PI + PI * float(i) / steps
		pts.append(c + Vector2(cos(a), sin(a)) * r)
	var h := r * 1.15
	var segs := 14
	for i in segs + 1:
		var f := float(i) / segs
		var x := c.x + r - 2.0 * r * f
		var y := c.y + h + sin(f * TAU * 2.5 + _t * 3.0) * r * 0.07
		pts.append(Vector2(x, y))
	draw_colored_polygon(pts, body)
	var outline := pts.duplicate()
	outline.append(pts[0])
	draw_polyline(outline, ink, 3.0, true)

	# Face
	var eye_y := c.y + r * 0.05
	var eye_dx := r * 0.36
	match mood:
		2: # sad: droopy brows
			draw_line(Vector2(c.x - eye_dx - 14, eye_y - 26), Vector2(c.x - eye_dx + 14, eye_y - 18), ink, 3.0)
			draw_line(Vector2(c.x + eye_dx + 14, eye_y - 26), Vector2(c.x + eye_dx - 14, eye_y - 18), ink, 3.0)
		1: # nervous: raised brows
			draw_line(Vector2(c.x - eye_dx - 14, eye_y - 18), Vector2(c.x - eye_dx + 14, eye_y - 28), ink, 3.0)
			draw_line(Vector2(c.x + eye_dx + 14, eye_y - 18), Vector2(c.x + eye_dx - 14, eye_y - 28), ink, 3.0)
	var eye_r := r * (0.13 if mood != 3 else 0.1)
	draw_circle(Vector2(c.x - eye_dx, eye_y), eye_r, ink)
	draw_circle(Vector2(c.x + eye_dx, eye_y), eye_r, ink)
	draw_circle(Vector2(c.x - eye_dx + 3, eye_y - 3), eye_r * 0.3, Color.WHITE)
	draw_circle(Vector2(c.x + eye_dx + 3, eye_y - 3), eye_r * 0.3, Color.WHITE)

	var my := c.y + r * 0.55
	var mw := r * 0.28
	var mouth := PackedVector2Array()
	for i in 11:
		var f := float(i) / 10.0
		var x := c.x - mw + 2.0 * mw * f
		var curve := sin(f * PI) * r * 0.1
		match mood:
			0: mouth.append(Vector2(x, my))
			1: mouth.append(Vector2(x, my + sin(f * TAU * 2.0) * 4.0))
			2: mouth.append(Vector2(x, my + 6.0 - curve))
			_: mouth.append(Vector2(x, my - 4.0 + curve))
	draw_polyline(mouth, ink, 3.0, true)
	draw_circle(Vector2(c.x - r * 0.62, my - 6), r * 0.1, Color(1.0, 0.6, 0.65, 0.35))
	draw_circle(Vector2(c.x + r * 0.62, my - 6), r * 0.1, Color(1.0, 0.6, 0.65, 0.35))

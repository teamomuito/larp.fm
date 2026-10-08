extends Node
## Tiny procedural sound effects, so the game needs no audio assets.

const RATE := 22050

var _streams := {}
var _player := AudioStreamPlayer.new()


func _ready() -> void:
	add_child(_player)
	_streams["stamp"] = _synth(0.22, func(t: float, d: float) -> float:
		var thump := sin(TAU * (110.0 - 70.0 * t / d) * t) * exp(-t * 16.0)
		var click := (randf() * 2.0 - 1.0) * exp(-t * 70.0) * 0.5
		return (thump + click) * 0.9)
	_streams["good"] = _synth(0.3, func(t: float, _d: float) -> float:
		var f := 660.0 if t < 0.09 else 880.0
		return sin(TAU * f * t) * 0.3 * exp(-t * 7.0))
	_streams["bad"] = _synth(0.35, func(t: float, _d: float) -> float:
		return (fposmod(t * 110.0, 1.0) * 2.0 - 1.0) * 0.35 * exp(-t * 6.0))


func play(sound: String) -> void:
	if _streams.has(sound):
		_player.stream = _streams[sound]
		_player.play()


func _synth(duration: float, fn: Callable) -> AudioStreamWAV:
	var n := int(RATE * duration)
	var data := PackedByteArray()
	data.resize(n * 2)
	for i in n:
		var v := clampf(float(fn.call(float(i) / RATE, duration)), -1.0, 1.0)
		data.encode_s16(i * 2, int(v * 30000.0))
	var w := AudioStreamWAV.new()
	w.format = AudioStreamWAV.FORMAT_16_BITS
	w.mix_rate = RATE
	w.stereo = false
	w.data = data
	return w

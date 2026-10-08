extends RefCounted
## Static content: names, causes, story souls, narrative text.

const MEADOW := 0
const ARCHIVE := 1
const FURNACE := 2
const RETURN := 3
const REALMS := ["Meadow", "Archive", "Furnace", "Return"]

const FIRST_NAMES := [
	"Ada", "Bram", "Cora", "Dov", "Elin", "Fenn", "Gus", "Hana", "Ivo", "Juno",
	"Kit", "Lena", "Milo", "Nell", "Orla", "Pip", "Quin", "Rosa", "Silas", "Tove",
	"Una", "Vik", "Wren", "Yara", "Zed",
]
const LAST_NAMES := [
	"Ashby", "Bellweather", "Crook", "Dunmore", "Eastwick", "Fairley", "Grimsby",
	"Holloway", "Ironside", "Jessop", "Kettle", "Lowry", "Marrow", "Nettleship",
	"Oakes", "Pennywhistle", "Quill", "Rook", "Stannard", "Thistle", "Underhill",
	"Vane", "Whitlock", "Yarrow",
]
const CAUSES := [
	"Old age", "Drowning", "Fever", "A fall", "Fire", "Poison", "Heartbreak",
	"Misadventure", "Lightning", "A bad oyster",
]
const ITEMS := [
	"Unpaid debt", "A pressed flower", "A stolen coin", "A love letter", "A small lie",
	"A rusty knife", "A pocket watch", "A lucky charm", "A map", "A borrowed hat",
]
const WEEKDAYS := ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
const SEALS := [
	{"name": "Violet", "color": Color(0.58, 0.32, 0.85)},
	{"name": "Teal", "color": Color(0.1, 0.65, 0.65)},
	{"name": "Amber", "color": Color(0.95, 0.65, 0.12)},
	{"name": "Crimson", "color": Color(0.8, 0.15, 0.25)},
	{"name": "Ash", "color": Color(0.6, 0.6, 0.62)},
]

const GENERIC_LINES := [
	"Is this the queue? I've lost my hat.",
	"Is it far? The walking, I mean.",
	"I was told there would be tea.",
	"Please be quick, I'm not good at waiting.",
	"I always did well on forms.",
	"Sorry. I'm still a bit see-through.",
	"Don't mind me. Just dead.",
	"Do I get a receipt?",
	"I had a whole week planned.",
	"You have kind hands. Ink-stained, but kind.",
	"Is that a lighthouse? I never saw the sea.",
	"I'd like to speak to your manager.",
	"I've been dead twenty minutes. It's fine. It's fine.",
	"Do they let you keep your shoes?",
	"I hope I paid the milkman.",
	"Left, or right? You can't tell, can you.",
]

## Mercy pleas. Waving one through to the Meadow when the rules say otherwise
## is "mercy": no citation, no credit, but the Auditor may notice.
const STORY := [
	{"day": 2, "name": "Odo Pennywhistle", "age": 81,
		"line": "I left the oven on. My apprentice won't know to turn it off. Please, send me to the Meadow, where the bread smells right."},
	{"day": 3, "name": "Mabel Quill", "age": 34,
		"line": "My daughter's birthday is tomorrow. I've been practising her song. If I forget it, I forget her. Please."},
	{"day": 4, "name": "Corvin Holloway", "age": 52,
		"line": "I lied for forty years. But I built the bridge. Three hundred people cross it daily. Doesn't that weigh something?"},
	{"day": 5, "name": "Elin Marrow", "age": 67,
		"line": "I nursed the sick until the last. Rules are rules, dear, but so is kindness."},
	{"day": 6, "name": "Dov Rook", "age": 29,
		"line": "My papers are wrong. I carried my brother's name so he could go first. He's behind me in the queue. Please."},
	{"day": 7, "name": "Ivo Thistle", "age": 44,
		"line": "I'd bribe you if I had anything left. Take my coat? It's a good coat."},
	{"day": 8, "name": "Tove Fairley", "age": 75,
		"line": "I only wanted to hear the sea once more. They say the Meadow has one."},
	{"day": 9, "name": "Wren Ashby", "age": 58,
		"line": "You're the new clerk? You look so tired. I once knew someone with your handwriting."},
]

const HOW_TO := """[b]Your job[/b]
Every soul arrives with a [b]LEDGER[/b] (their life record) and a [b]DEATH SLIP[/b]. Check both against the [b]RULEBOOK[/b], then stamp a verdict:

[color=#7fd6a0][b]MEADOW[/b][/color]  rest
[color=#8fb0f0][b]ARCHIVE[/b][/color]  be remembered
[color=#f09060][b]FURNACE[/b][/color]  burn the weight off
[color=#b4b4c4][b]RETURN[/b][/color]  papers invalid, or not their time

The rules are checked [b]in order[/b]. The first one that applies decides the verdict. If nothing applies, the soul's weight decides.

[b]Tools[/b]
Tap any field to circle it with your pen. The [b]Lens[/b] reveals mismatched papers (3 per shift).

[b]Debt[/b]
Correct stamp: -3. Wrong stamp: +7. Interest: +6 every night. Clear your debt to leave. Let it reach 350 and you'll be stoking the Furnace.

[b]Mercy[/b]
Some souls plead their case. You can wave them into the Meadow against the rules. It costs you nothing at once, but the Auditor reads the stamps."""


static func day_intro(day: int) -> String:
	match day:
		1:
			return "You wake at a desk in the Lighthouse Customs for the Dead. A brass plate reads [b]CLERK (PROBATIONARY)[/b]. The Warden slides a ledger across the desk.\n\n\"You owe the Afterlife 280 shards. Stamp souls correctly and the debt shrinks. Stamp them wrongly and it grows. Interest is charged nightly. Welcome.\""
		2:
			return "\"Some of them lie about who they are,\" says the Warden. \"The ledger and the death slip must agree. Names. Ages. Check them both.\""
		3:
			return "A crate of confiscations has been left beside your desk. \"Contraband,\" the Warden says. \"Today it's one thing. Tomorrow it'll be another. Keep up.\""
		4:
			return "\"The Furnace is running low,\" the Warden says, not looking at you. \"Certain deaths burn hotter than others. See that they arrive.\""
		5:
			return "A memo, stamped URGENT: children are not to be weighed. Whatever the scales say, they go to the Meadow. The Warden underlined the word [i]whatever[/i] twice."
		6:
			return "\"The Archive wants filling,\" says the Warden. \"They've taken a liking to a particular day of the week. Don't ask me why.\""
		7:
			return "The scales were recalibrated overnight. The numbers on the dial mean something different now. Check the rulebook before you trust your habits."
		8:
			return "\"Some of them have come too early,\" says the Warden. \"Death is a schedule, clerk. We send back the ones who've jumped the queue.\""
		9:
			return "Overnight, the Furnace list was rewritten. The Warden is smiling for the first time since you arrived. You don't like it."
		10:
			return "The last shift on the schedule. In the corner of the room, beneath a cloth, a tray holds a single file. It isn't time to look yet."
		_:
			return "Overtime. The Warden says nothing. A fresh stack of files waits on your desk. The debt is still there, patient as the sea."


static func day_end(day: int) -> String:
	match day:
		1:
			return "The lamp in the lighthouse turns. You've learned how the stamp feels in your hand."
		2:
			return "Someone has left a cup of tea on your desk. It has been cold for a very long time."
		3:
			return "Under your blotter you find a folded file card. The name is smudged. Cause of death: [i]ask the Warden[/i]. You put it back."
		4:
			return "The Furnace roars all night. Somewhere below, someone is singing."
		5:
			return "You catch yourself humming the tune Mabel Quill was practising."
		6:
			return "The file card is on top of the blotter again. Cause of death now reads [i]unfinished[/i]. Weight: [i]heavy, but not with sin[/i]."
		7:
			return "Your hands ache. You can't remember what you used to do with them."
		8:
			return "The sea is audible tonight. The Warden shuts the window."
		9:
			return "The card lies face up. The name is no longer smudged. You read it twice."
		_:
			return "The lamp turns. The debt waits."


const ENDINGS := {
	"meadow": {
		"title": "The Meadow",
		"kind": "Grass, light, and the sound of a sea you can finally hear. The souls you waved through are waiting at the gate with Odo's bread, still warm. \"Took you long enough,\" says Mabel, and hums you a song.",
		"strict": "You're sent to the Meadow exactly as the rules require. It is calm and green and quiet. Nobody here knows your name, and the longer you walk, the more you realise you never gave them a reason to learn it.",
	},
	"archive": {
		"title": "The Archive",
		"kind": "You become a shelf of stories: every soul you spared is cross-referenced under your name. Visitors ask for \"the Clerk's mercies\" and the pages fall open by themselves.",
		"strict": "You are filed correctly, alphabetically, and in triplicate. Centuries later someone requests your folder. It is perfect. They wonder who you were.",
	},
	"furnace": {
		"title": "The Furnace",
		"kind": "You step into the flames and they are warm, not cruel. They were always meant to keep something alive. Above you the lighthouse burns brighter, and a lost soul finds the shore by it.",
		"strict": "The Furnace takes you without ceremony. The rules were clear, and you followed them down to the last one. The lamp burns a little brighter tonight. No one thinks to ask why.",
	},
	"stay": {
		"title": "The Warden's Stamp",
		"kind": "You pick up the stamp and rewrite the rulebook. Rule one: when in doubt, the Meadow. The Warden watches you do it. Then, very quietly, they take off their hat and leave.",
		"strict": "You take the Warden's stamp. The Warden's face is a blank page. \"Good,\" it says, and then it is only a hat on a hook. The rulebook is yours to keep. The queue is long. It always was.",
	},
}

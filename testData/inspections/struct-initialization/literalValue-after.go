package foo

func main() {
	type B struct {
		string
	}
	type S struct {
		int
		B
	}
	_ = []S{ {int: 1, B: {string: "a"}}}
}
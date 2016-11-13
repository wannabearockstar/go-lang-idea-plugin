package foo

func main() {
	type B struct {
		string
	}
	type S struct {
		B
	}
	_ = []S{ { {"a"} } }
}
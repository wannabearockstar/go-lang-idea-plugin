package foo

func main() {
	type B struct {
		string
	}
	type S struct {
		int
		B
	}
	_ = []S{ {<weak_warning descr="Unnamed field initialization">1<caret></weak_warning>, B: {string: "a"}}}
}
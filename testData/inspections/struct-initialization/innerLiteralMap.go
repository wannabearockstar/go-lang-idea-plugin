package foo

type S struct {
	r, t int
}

func main() {
	var _ = map[string] []S{
		"s": {
			{<weak_warning descr="Unnamed field initialization">1<caret></weak_warning>, <weak_warning descr="Unnamed field initialization">1</weak_warning>},
	},
}
}
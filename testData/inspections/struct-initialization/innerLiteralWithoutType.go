package main

type B struct {
	t int
}

type S struct {
	b B
	a int
}

func main() {
	_ = map[string]S{
		"key": { <weak_warning descr="Unnamed field initialization"><caret>B{t: 1}</weak_warning> },
	}
}
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
		"key": {b: B{t: 1} },
	}
}